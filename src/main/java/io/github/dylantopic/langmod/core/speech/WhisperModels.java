package io.github.dylantopic.langmod.core.speech;

import java.nio.file.Path;

/** Where Whisper models live and where to get them. */
public final class WhisperModels {
	/** Folder listing all ggml models (whisper.cpp's official conversions). */
	public static final String DOWNLOAD_PAGE = "https://huggingface.co/ggerganov/whisper.cpp/tree/main";

	/** Whisper's encoder window: 30 s of audio in 20 ms frames. */
	public static final int FULL_AUDIO_CONTEXT = 1500;
	private static final int SAMPLES_PER_FRAME = 320; // 20 ms at 16 kHz
	private static final int AUTO_MARGIN_FRAMES = 64;
	private static final int AUTO_MIN_FRAMES = 128;

	private WhisperModels() {
	}

	/** {@code small} -> {@code ggml-small.bin}; quantised variants work too ({@code small-q5_1}). */
	public static String fileName(String modelSize) {
		return "ggml-" + modelSize + ".bin";
	}

	public static Path modelFile(Path modelsDirectory, String modelSize) {
		return modelsDirectory.resolve(fileName(modelSize));
	}

	/** Direct download link for one model file. */
	public static String downloadUrl(String modelSize) {
		return "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/" + fileName(modelSize);
	}

	/**
	 * whisper.cpp's audio_ctx for a clip. Whisper always encodes a 30 second window (1500 frames of
	 * 20 ms), even for a one second word; encoding only the clip's length plus a margin is several
	 * times faster.
	 *
	 * @param setting 0 = full window, &gt; 0 = that many frames, &lt; 0 = automatic (clip length + margin)
	 */
	public static int audioContext(int samples, int setting) {
		if (setting >= 0) {
			return Math.min(setting, FULL_AUDIO_CONTEXT);
		}

		int frames = (samples + SAMPLES_PER_FRAME - 1) / SAMPLES_PER_FRAME;
		return Math.min(FULL_AUDIO_CONTEXT, Math.max(AUTO_MIN_FRAMES, frames + AUTO_MARGIN_FRAMES));
	}

	/** Threads for Whisper when the config says "auto": half the cores, so Minecraft keeps the rest. */
	public static int autoThreads() {
		int cores = Runtime.getRuntime().availableProcessors();
		return Math.max(1, Math.min(8, cores / 2));
	}
}
