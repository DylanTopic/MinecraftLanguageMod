package io.github.dylantopic.langmod.core.speech;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

import io.github.givimad.whisperjni.WhisperContext;
import io.github.givimad.whisperjni.WhisperContextParams;
import io.github.givimad.whisperjni.WhisperFullParams;
import io.github.givimad.whisperjni.WhisperJNI;
import io.github.givimad.whisperjni.WhisperSamplingStrategy;

/**
 * Runs whisper.cpp inside the game process through the whisper-jni bindings
 * (io.github.givimad:whisper-jni, which bundles native libraries for Windows x64, Linux and macOS).
 * The bundled build is CPU only; use {@link WhisperServerEngine} for GPU acceleration.
 */
public final class WhisperJniEngine implements SpeechEngine {
	private final Path model;
	private final int threads;
	private final boolean useGpu;
	private final Consumer<String> log;
	private WhisperJNI whisper;
	private WhisperContext context;
	private String systemInfo = "";

	public WhisperJniEngine(Path model, int threads, boolean useGpu, Consumer<String> log) {
		this.model = model;
		this.threads = threads;
		this.useGpu = useGpu;
		this.log = log;
	}

	@Override
	public void load() throws IOException {
		if (!Files.isRegularFile(model)) {
			throw new IOException("Model file not found: " + model);
		}

		try {
			WhisperJNI.loadLibrary(log::accept);
		} catch (UnsatisfiedLinkError e) {
			throw new IOException("Could not load the whisper.cpp native library (" + e.getMessage() + "). On Windows this "
					+ "usually means the Microsoft Visual C++ 2015-2022 x64 runtime is missing (https://aka.ms/vs/17/release/vc_redist.x64.exe), "
					+ "or the CPU lacks AVX2.", e);
		}

		WhisperJNI.setLibraryLogger(log::accept);
		whisper = new WhisperJNI();
		systemInfo = whisper.getSystemInfo();
		WhisperContextParams params = new WhisperContextParams();
		params.useGPU = useGpu;
		context = whisper.init(model, params);

		if (context == null) {
			throw new IOException("whisper.cpp could not load " + model.getFileName() + " (corrupt or not a ggml model?)");
		}
	}

	@Override
	public String transcribe(float[] samples, Request request) throws IOException {
		WhisperFullParams params = new WhisperFullParams(WhisperSamplingStrategy.GREEDY);
		params.nThreads = threads;
		params.language = request.language();
		params.detectLanguage = false;
		params.translate = false;
		params.noContext = true;
		params.singleSegment = true;
		params.noTimestamps = true;
		params.suppressBlank = true;
		params.suppressNonSpeechTokens = true;
		params.printProgress = false;
		params.printRealtime = false;
		params.printTimestamps = false;
		params.printSpecial = false;
		params.temperature = 0f;
		params.initialPrompt = request.initialPrompt();
		params.audioCtx = request.audioContext();

		int result = whisper.full(context, params, samples, samples.length);

		if (result != 0) {
			throw new IOException("whisper_full failed with code " + result);
		}

		StringBuilder text = new StringBuilder();
		int segments = whisper.fullNSegments(context);

		for (int i = 0; i < segments; i++) {
			text.append(whisper.fullGetSegmentText(context, i));
		}

		return text.toString().trim();
	}

	@Override
	public String describe() {
		boolean gpu = systemInfo.contains("CUDA = 1") || systemInfo.contains("METAL = 1") || systemInfo.contains("VULKAN = 1");
		return "whisper.cpp 1.7.1 (JNI) " + model.getFileName() + ", " + threads + " threads, " + (gpu ? "GPU" : "CPU");
	}

	@Override
	public void close() {
		if (context != null) {
			context.close();
			context = null;
		}
	}
}
