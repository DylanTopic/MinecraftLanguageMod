package io.github.dylantopic.langmod.client;

import net.minecraft.client.Minecraft;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import io.github.dylantopic.langmod.LangMod;
import io.github.dylantopic.langmod.LangModConfig;
import io.github.dylantopic.langmod.client.audio.MicCapture;
import io.github.dylantopic.langmod.client.speech.SpeechService;
import io.github.dylantopic.langmod.net.AttemptResultPayload;
import io.github.dylantopic.langmod.net.ClientPhasePayload;
import io.github.dylantopic.langmod.net.TranscriptionPayload;

/**
 * Runs the microphone during a challenge: open it when the challenge starts, report speaking /
 * checking to the server (which pauses the countdown), transcribe each utterance in the background
 * and send the text to the server, and close the microphone when the challenge ends.
 * Only touched on the render thread (microphone callbacks hop over to it).
 */
public final class ChallengeController {
	private static final ChallengeController INSTANCE = new ChallengeController();
	/** Pause after a wrong answer before listening again, so the result can be read. */
	private static final int RESUME_DELAY_TICKS = 15;

	private MicCapture mic;
	private int challengeId = -1;
	private String expectedHanzi;
	private boolean checking;
	private int resumeIn;
	private boolean micBroken;
	private String micError = "";
	/** Game tests run without a microphone and submit clips directly. */
	private boolean useMicrophone = true;

	private ChallengeController() {
	}

	public static ChallengeController get() {
		return INSTANCE;
	}

	public void setUseMicrophoneForTesting(boolean useMicrophone) {
		this.useMicrophone = useMicrophone;
	}

	/** False after the microphone failed; challenges are paused until /langmod test works again. */
	public boolean micAvailable() {
		return !micBroken;
	}

	public String micError() {
		return micError;
	}

	public void clearMicError() {
		if (micBroken) {
			micBroken = false;
			micError = "";
			LangModClient.sendHello();
		}
	}

	public MicCapture mic() {
		return mic;
	}

	public boolean checking() {
		return checking;
	}

	public void onChallengeStart(ClientChallenge.Active active) {
		closeMic();
		challengeId = active.id();
		expectedHanzi = active.entry().hanzi();
		checking = false;
		resumeIn = 0;

		if (!useMicrophone) {
			return;
		}

		try {
			mic = MicCapture.open(LangModConfig.get().micDevice, MicTest.vadConfig(), listener(challengeId));
		} catch (Exception e) {
			reportMicFailure("Could not open the microphone: " + e.getMessage());
		}
	}

	public void onChallengeEnd() {
		closeMic();
		challengeId = -1;
		checking = false;
	}

	public void onAttemptResult(AttemptResultPayload result) {
		if (result.challengeId() != challengeId) {
			return;
		}

		checking = false;

		if (result.verdict() != AttemptResultPayload.Verdict.CORRECT) {
			resumeIn = RESUME_DELAY_TICKS;
		}
	}

	public void tick(Minecraft minecraft) {
		if (mic == null) {
			return;
		}

		boolean pauseMenu = minecraft.screen != null && minecraft.screen.isPauseScreen();

		if (resumeIn > 0) {
			resumeIn--;
		}

		// Listen unless Whisper is busy, a result is being shown, or the game is paused.
		mic.setPaused(checking || resumeIn > 0 || pauseMenu);
	}

	/** Sends a transcription for the current challenge as if it had come from the microphone (game tests). */
	public void submitClipForTesting(float[] samples) {
		if (challengeId >= 0) {
			onClip(challengeId, samples);
		}
	}

	private MicCapture.Listener listener(int id) {
		Minecraft minecraft = Minecraft.getInstance();
		return new MicCapture.Listener() {
			@Override
			public void onSpeechStart() {
				minecraft.execute(() -> sendPhase(id, ClientPhasePayload.Phase.RECORDING));
			}

			@Override
			public void onClip(float[] samples) {
				minecraft.execute(() -> ChallengeController.this.onClip(id, samples));
			}

			@Override
			public void onDiscarded() {
				minecraft.execute(() -> sendPhase(id, ClientPhasePayload.Phase.LISTENING));
			}

			@Override
			public void onError(String message) {
				minecraft.execute(() -> reportMicFailure("Microphone error: " + message));
			}
		};
	}

	private void onClip(int id, float[] samples) {
		if (id != challengeId || checking) {
			return;
		}

		checking = true;

		if (mic != null) {
			mic.setPaused(true);
		}

		sendPhase(id, ClientPhasePayload.Phase.CHECKING);
		ClientChallenge.get().onChecking(samples.length / 16);
		long start = System.nanoTime();

		SpeechService.get().transcribe(samples, expectedHanzi).whenComplete((transcript, error) -> Minecraft.getInstance().execute(() -> {
			if (id != challengeId) {
				return; // the challenge ended meanwhile
			}

			if (error != null) {
				String reason = MicTest.rootMessage(error);
				LangMod.LOGGER.warn("Transcription failed: {}", reason);
				send(new TranscriptionPayload(id, false, reason, (int) ((System.nanoTime() - start) / 1_000_000)));
			} else {
				send(new TranscriptionPayload(id, true, transcript.text(), (int) transcript.millis()));
			}
		}));
	}

	private void reportMicFailure(String message) {
		LangMod.LOGGER.error(message);
		closeMic();
		micBroken = true;
		micError = message;
		ClientMessages.error(message + ". Challenges are paused and your items were given back; check /langmod mics, then run /langmod test.");
		// Tell the server we cannot take part right now, so it hands the items back instead of letting them vanish.
		LangModClient.sendHello();
	}

	private void sendPhase(int id, ClientPhasePayload.Phase phase) {
		if (id == challengeId) {
			send(new ClientPhasePayload(id, phase));
		}
	}

	private static void send(net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
		if (Minecraft.getInstance().getConnection() != null && ClientPlayNetworking.canSend(payload.type())) {
			ClientPlayNetworking.send(payload);
		}
	}

	private void closeMic() {
		if (mic != null) {
			mic.close();
			mic = null;
		}
	}
}
