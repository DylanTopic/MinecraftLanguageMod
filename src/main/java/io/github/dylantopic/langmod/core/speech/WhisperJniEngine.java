package io.github.dylantopic.langmod.core.speech;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Locale;
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
	/** whisper-jni loads its native library from this directory, if set, instead of extracting it itself. */
	private static final String LIBRARY_DIR_PROPERTY = "io.github.givimad.whisperjni.libdir";
	/** The Windows build with whisper.cpp linked in, inside the whisper-jni jar. */
	private static final String WINDOWS_LIBRARY = "/win-amd64/whisper-jni_full.dll";
	/** Keep in step with whisper_jni_version in gradle.properties. */
	private static final String WHISPER_JNI_VERSION = "1.7.1";

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

		loadNativeLibrary(log);
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

	/**
	 * Loads whisper-jni's native library. On Windows, whisper-jni would first look for a separately
	 * installed whisper.dll by turning every entry of the PATH environment variable into a Path, which
	 * throws InvalidPathException if any entry is malformed (for example
	 * {@code ...\AppData\Roaming\C:\Program Files\ffmpeg.exe}). Extracting the self-contained DLL
	 * ourselves and pointing whisper-jni at it skips that search.
	 */
	public static void loadNativeLibrary(Consumer<String> log) throws IOException {
		if (System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")) {
			Path dir = Path.of(System.getProperty("java.io.tmpdir"), "langmod-whisper-jni-" + WHISPER_JNI_VERSION);
			extractWindowsLibrary(dir);
			System.setProperty(LIBRARY_DIR_PROPERTY, dir.toString());
		}

		try {
			WhisperJNI.loadLibrary(log::accept);
		} catch (UnsatisfiedLinkError e) {
			throw new IOException("Could not load the whisper.cpp native library (" + e.getMessage() + "). On Windows this "
					+ "usually means the Microsoft Visual C++ 2015-2022 x64 runtime is missing (https://aka.ms/vs/17/release/vc_redist.x64.exe), "
					+ "or the CPU lacks AVX2.", e);
		}
	}

	/** Writes the self-contained Windows DLL as {@code dir/whisper-jni.dll}, the name whisper-jni loads, unless it is already there. */
	static Path extractWindowsLibrary(Path dir) throws IOException {
		byte[] library;

		try (InputStream in = WhisperJNI.class.getResourceAsStream(WINDOWS_LIBRARY)) {
			if (in == null) {
				throw new IOException(WINDOWS_LIBRARY + " is missing from the whisper-jni jar");
			}

			library = in.readAllBytes();
		}

		Path target = dir.resolve("whisper-jni.dll");

		if (Files.isRegularFile(target) && Arrays.equals(Files.readAllBytes(target), library)) {
			return target; // extracted on an earlier start (and possibly loaded by another running game)
		}

		Files.createDirectories(dir);
		Path temp = Files.createTempFile(dir, "whisper-jni", ".tmp");

		try {
			Files.write(temp, library);
			Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
		} finally {
			Files.deleteIfExists(temp);
		}

		return target;
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
