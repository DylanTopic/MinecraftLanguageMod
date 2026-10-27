package io.github.dylantopic.langmod.client;

import java.util.Locale;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import io.github.dylantopic.langmod.LangMod;
import io.github.dylantopic.langmod.LangModConfig;
import io.github.dylantopic.langmod.TextData;
import io.github.dylantopic.langmod.client.audio.MicCapture;
import io.github.dylantopic.langmod.client.speech.SpeechService;
import io.github.dylantopic.langmod.core.audio.Vad;

/**
 * {@code /langmod test}: opens the microphone, records one utterance, transcribes it and prints
 * the raw output, the simplified text, its pinyin and the timings.
 */
public final class MicTest {
	private static final int LISTEN_TICKS = 20 * 10;
	private static MicTest current;
	private static String lastHeard;

	private MicCapture mic;
	private int ticksLeft = LISTEN_TICKS;
	private boolean checking;

	private MicTest() {
	}

	public static MicTest current() {
		return current;
	}

	/** Simplified text of the last completed test (for game tests). */
	public static String lastHeard() {
		return lastHeard;
	}

	public MicCapture mic() {
		return mic;
	}

	public boolean checking() {
		return checking;
	}

	public static void start() {
		if (current != null) {
			ClientMessages.info("A microphone test is already running.");
			return;
		}

		SpeechService speech = SpeechService.get();

		if (!speech.ready()) {
			ClientMessages.error(speech.statusMessage());
			return;
		}

		MicTest test = new MicTest();

		try {
			test.mic = MicCapture.open(LangModConfig.get().micDevice, vadConfig(), test.listener());
		} catch (Exception e) {
			ClientMessages.error("Could not open the microphone: " + e.getMessage() + " (see /langmod mics)");
			return;
		}

		current = test;
		ClientMessages.info("Microphone on (" + test.mic.deviceName() + "). Say something in Chinese...");
	}

	/** Runs the test on a given clip instead of the microphone (game tests). */
	public static void runOnClipForTesting(float[] samples) {
		MicTest test = new MicTest();
		current = test;
		test.transcribe(samples);
	}

	public static void tick() {
		MicTest test = current;

		if (test != null && !test.checking && test.mic != null && !test.mic.inSpeech() && --test.ticksLeft <= 0) {
			test.finish();
			ClientMessages.error(String.format(Locale.ROOT, "No speech detected. Level %.0f dB, speech threshold %.0f dB. "
					+ "Check /langmod mics and the micDevice / vadThresholdDb settings.", test.mic.levelDb(), test.mic.thresholdDb()));
		}
	}

	public static Vad.Config vadConfig() {
		LangModConfig config = LangModConfig.get();
		return new Vad.Config((float) config.vadThresholdDb, config.vadSilenceMs, config.vadMaxClipMs, config.vadMinSpeechMs);
	}

	private MicCapture.Listener listener() {
		return new MicCapture.Listener() {
			@Override
			public void onSpeechStart() {
			}

			@Override
			public void onClip(float[] samples) {
				Minecraft.getInstance().execute(() -> transcribe(samples));
			}

			@Override
			public void onError(String message) {
				Minecraft.getInstance().execute(() -> {
					finish();
					ClientMessages.error("Microphone error: " + message);
				});
			}
		};
	}

	private void transcribe(float[] samples) {
		if (current != this || checking) {
			return;
		}

		checking = true;

		// Close the microphone right away: we only ever record while actively listening.
		if (mic != null) {
			mic.close();
		}

		double seconds = samples.length / (double) Vad.SAMPLE_RATE;
		ClientMessages.info(String.format(Locale.ROOT, "Checking %.1f s of audio...", seconds));

		SpeechService.get().transcribe(samples, null).whenComplete((transcript, error) -> Minecraft.getInstance().execute(() -> {
			current = null;

			if (error != null) {
				ClientMessages.error("Transcription failed: " + rootMessage(error));
				return;
			}

			String simplified = TextData.get().normalizer().normalize(transcript.text());
			String pinyin = TextData.get().defaultPinyin(simplified);
			lastHeard = simplified;
			LangMod.LOGGER.info("LANGMOD-MICTEST raw='{}' simplified='{}' pinyin='{}' ms={}", transcript.text(), simplified, pinyin, transcript.millis());
			ClientMessages.send(Component.literal("Heard: ").withStyle(ChatFormatting.GRAY)
					.append(Component.literal(simplified.isEmpty() ? "(nothing)" : simplified).withStyle(ChatFormatting.WHITE))
					.append(Component.literal("  " + pinyin).withStyle(ChatFormatting.AQUA)));
			ClientMessages.info("Raw Whisper output: \"" + transcript.text() + "\"");
			ClientMessages.info(String.format(Locale.ROOT, "Audio %.1f s, transcription %d ms (%s)", seconds, transcript.millis(), transcript.engine()));
		}));
	}

	private void finish() {
		if (mic != null) {
			mic.close();
		}

		current = null;
	}

	static String rootMessage(Throwable error) {
		Throwable t = error;

		while (t.getCause() != null && t.getCause() != t) {
			t = t.getCause();
		}

		if (t instanceof java.util.concurrent.TimeoutException) {
			return "took longer than " + LangModConfig.get().transcriptionTimeoutSeconds + " s";
		}

		return t.getMessage() == null ? t.toString() : t.getMessage();
	}
}
