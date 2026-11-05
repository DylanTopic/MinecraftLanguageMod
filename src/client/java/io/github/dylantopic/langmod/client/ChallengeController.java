package io.github.dylantopic.langmod.client;

import net.minecraft.client.Minecraft;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import io.github.dylantopic.langmod.LangMod;
import io.github.dylantopic.langmod.LangModConfig;
import io.github.dylantopic.langmod.client.audio.MicCapture;
import io.github.dylantopic.langmod.client.speech.SpeechService;
import io.github.dylantopic.langmod.client.villager.VillagerConversationScreen;
import io.github.dylantopic.langmod.client.voice.Pronouncer;
import io.github.dylantopic.langmod.core.lang.Language;
import io.github.dylantopic.langmod.core.lang.NameEntry;
import io.github.dylantopic.langmod.net.AttemptResultPayload;
import io.github.dylantopic.langmod.net.ChallengeEndPayload;
import io.github.dylantopic.langmod.net.ClientPhasePayload;
import io.github.dylantopic.langmod.net.TranscriptionPayload;

/**
 * Runs the microphone during a challenge: open it when the challenge starts, report speaking /
 * checking to the server (which pauses the countdown), transcribe each utterance in the background
 * and send the text to the server, and close the microphone when the challenge ends. After a wrong
 * answer, and when an item is lost, the name is said out loud ({@link Pronouncer}); the microphone
 * is paused and the countdown waits until the voice has stopped.
 * Only touched on the render thread (microphone callbacks hop over to it).
 */
public final class ChallengeController {
	private static final ChallengeController INSTANCE = new ChallengeController();
	/** Pause after a wrong answer before listening again, so the result can be read. */
	private static final int RESUME_DELAY_TICKS = 15;

	private MicCapture mic;
	private int challengeId = -1;
	private Language language;
	private NameEntry entry;
	private boolean checking;
	/** Whether the server was last told that the name is being said. */
	private boolean hearingSent;
	/** The last name got wrong (for the "hear it again" key), and the challenge it was in. */
	private Language mistakeLanguage;
	private NameEntry mistake;
	private int mistakeChallengeId = -1;
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

	/** Whether the name is being said (the microphone is paused and the countdown waits meanwhile). */
	public boolean hearing() {
		return Pronouncer.get().busy();
	}

	public void onChallengeStart(ClientChallenge.Active active) {
		closeMic();
		challengeId = active.id();
		language = active.language();
		entry = active.entry();
		checking = false;
		hearingSent = false;
		resumeIn = 0;

		if (LangModConfig.get().sayNameWhenWrong) {
			Pronouncer.get().prepare(language, entry);
		}

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

	/** A challenge ended. An item lost to the clock is said now (a last wrong answer already was). */
	public void onChallengeEnd(ChallengeEndPayload.Outcome outcome, ClientChallenge.Active ended) {
		onChallengeEnd();

		if (outcome == ChallengeEndPayload.Outcome.FAILED) {
			rememberMistake(ended.language(), ended.entry(), ended.id());

			if (LangModConfig.get().sayNameWhenWrong && !Pronouncer.get().busy()) {
				Pronouncer.get().say(ended.language(), ended.entry());
			}
		}
	}

	public void onAttemptResult(AttemptResultPayload result) {
		if (result.challengeId() != challengeId) {
			return;
		}

		checking = false;

		if (result.verdict() != AttemptResultPayload.Verdict.CORRECT) {
			resumeIn = RESUME_DELAY_TICKS;
		}

		if (result.verdict() == AttemptResultPayload.Verdict.WRONG) {
			rememberMistake(language, entry, challengeId);

			if (LangModConfig.get().sayNameWhenWrong) {
				Pronouncer.get().say(language, entry);
			}
		}
	}

	/**
	 * The "hear it again" key: says the name last got wrong, in the current challenge once it has
	 * been got wrong there (never before, that would give the answer away), otherwise the last item lost.
	 */
	public void hearAgain() {
		if (mistake == null || checking || Pronouncer.get().busy() || (challengeId >= 0 && challengeId != mistakeChallengeId)) {
			return;
		}

		Pronouncer.get().say(mistakeLanguage, mistake);
	}

	private void rememberMistake(Language mistakeLanguage, NameEntry mistake, int challenge) {
		this.mistakeLanguage = mistakeLanguage;
		this.mistake = mistake;
		this.mistakeChallengeId = challenge;
	}

	public void tick(Minecraft minecraft) {
		boolean hearing = hearing();

		// The countdown waits while the name is said and goes on when the voice (and its echo) has stopped.
		if (challengeId >= 0 && hearing != hearingSent) {
			hearingSent = hearing;

			if (hearing || !checking) {
				sendPhase(challengeId, hearing ? ClientPhasePayload.Phase.HEARING : ClientPhasePayload.Phase.LISTENING);
			}
		}

		if (mic == null) {
			return;
		}

		// A conversation with a villager has its own microphone.
		boolean pauseMenu = minecraft.screen != null && (minecraft.screen.isPauseScreen() || minecraft.screen instanceof VillagerConversationScreen);

		if (resumeIn > 0) {
			resumeIn--;
		}

		// Listen unless Whisper is busy, a result is being shown, the name is being said, or the game is paused.
		mic.setPaused(checking || resumeIn > 0 || hearing || pauseMenu);
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

		SpeechService.get().transcribe(samples, language, entry.text()).whenComplete((transcript, error) -> Minecraft.getInstance().execute(() -> {
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
