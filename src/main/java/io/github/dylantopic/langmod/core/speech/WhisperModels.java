package io.github.dylantopic.langmod.core.speech;

import java.nio.file.Path;

/** Where Whisper models live and where to get them. */
public final class WhisperModels {
	/** Folder listing all ggml models (whisper.cpp's official conversions). */
	public static final String DOWNLOAD_PAGE = "https://huggingface.co/ggerganov/whisper.cpp/tree/main";

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

	/** Threads for Whisper when the config says "auto": half the cores, so Minecraft keeps the rest. */
	public static int autoThreads() {
		int cores = Runtime.getRuntime().availableProcessors();
		return Math.max(1, Math.min(8, cores / 2));
	}
}
