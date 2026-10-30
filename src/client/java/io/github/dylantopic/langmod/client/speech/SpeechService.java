package io.github.dylantopic.langmod.client.speech;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import io.github.dylantopic.langmod.LangMod;
import io.github.dylantopic.langmod.LangModConfig;
import io.github.dylantopic.langmod.core.lang.Language;
import io.github.dylantopic.langmod.core.speech.SpeechEngine;
import io.github.dylantopic.langmod.core.speech.WhisperJniEngine;
import io.github.dylantopic.langmod.core.speech.WhisperModels;
import io.github.dylantopic.langmod.core.speech.WhisperServerEngine;

/**
 * Owns the speech engine. The model is loaded once on a background thread at game start, and
 * every transcription runs on that same thread, so neither the render thread nor the integrated
 * server is ever blocked.
 */
public final class SpeechService {
	public enum Status {
		LOADING,
		READY,
		/** No model file: the user has to download one (see {@link #statusMessage()}). */
		MODEL_MISSING,
		FAILED
	}

	/** Result of one transcription; {@code millis} is the time Whisper took. */
	public record Transcript(String text, long millis, String engine) {
	}

	private static final SpeechService INSTANCE = new SpeechService();
	private static final String TEST_ENGINE = "test";

	private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "LangMod-Speech");
		thread.setDaemon(true);
		thread.setPriority(Thread.NORM_PRIORITY - 1);
		return thread;
	});
	private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
	private volatile Status status = Status.LOADING;
	private volatile String statusMessage = "Loading speech recognition...";
	private volatile String description = "";
	/** Only touched on the speech thread. */
	private SpeechEngine engine;
	private String engineSettings;

	private SpeechService() {
	}

	public static SpeechService get() {
		return INSTANCE;
	}

	public static Path modelsDirectory() {
		return LangModConfig.directory().resolve("models");
	}

	/** Loads (or reloads, if the speech settings changed) the engine in the background. */
	public void start() {
		executor.execute(this::loadIfNeeded);
	}

	/** Called when the config changed; reloads only if engine-related settings differ. */
	public void reload() {
		start();
	}

	/** Uses a given engine instead of Whisper (game tests). It stays in use until the game closes. */
	public void useEngineForTesting(SpeechEngine testEngine) {
		executor.execute(() -> {
			closeEngine();
			engine = testEngine;
			engineSettings = TEST_ENGINE;

			try {
				testEngine.load();
				description = testEngine.describe();
				setStatus(Status.READY, "Ready (" + description + ")");
			} catch (Exception e) {
				setStatus(Status.FAILED, "Test engine failed: " + e.getMessage());
			}
		});
	}

	/**
	 * Transcribes a clip in the background. Fails (instead of hanging) if the engine is not ready,
	 * throws, or takes longer than the configured timeout.
	 *
	 * @param language the language Whisper is told to expect
	 * @param expectedName used as Whisper's initial prompt if prompt biasing is enabled (otherwise the
	 *     language's generic prompt is used); may be null
	 */
	public CompletableFuture<Transcript> transcribe(float[] samples, Language language, String expectedName) {
		if (status != Status.READY) {
			return CompletableFuture.failedFuture(new IllegalStateException(statusMessage));
		}

		LangModConfig config = LangModConfig.get();
		String prompt = config.promptBiasing && expectedName != null ? expectedName : config.whisperPrompt(language);
		SpeechEngine.Request request = new SpeechEngine.Request(language.whisperCode(), prompt,
				WhisperModels.audioContext(samples.length, config.audioContext));
		CompletableFuture<Transcript> result = new CompletableFuture<>();

		executor.execute(() -> {
			if (result.isDone()) {
				return; // timed out while queued
			}

			long start = System.nanoTime();

			try {
				String text = engine.transcribe(samples, request);
				result.complete(new Transcript(text, (System.nanoTime() - start) / 1_000_000, description));
			} catch (Throwable t) {
				LangMod.LOGGER.warn("Transcription failed", t);
				result.completeExceptionally(t);
			}
		});

		return result.orTimeout(config.transcriptionTimeoutSeconds, TimeUnit.SECONDS);
	}

	public Status status() {
		return status;
	}

	public boolean ready() {
		return status == Status.READY;
	}

	public String statusMessage() {
		return statusMessage;
	}

	public String description() {
		return description;
	}

	/** Notified (on the speech thread) whenever the status changes. */
	public void addListener(Runnable listener) {
		listeners.add(listener);
	}

	public void shutdown() {
		executor.execute(this::closeEngine);
		executor.shutdown();
	}

	private void loadIfNeeded() {
		if (TEST_ENGINE.equals(engineSettings)) {
			return;
		}

		LangModConfig config = LangModConfig.get();
		int threads = config.whisperThreads > 0 ? config.whisperThreads : WhisperModels.autoThreads();
		Path model = WhisperModels.modelFile(modelsDirectory(), config.modelSize);
		String settings = String.join("|", config.speechEngine, model.toString(), Integer.toString(threads),
				Boolean.toString(config.useGpu), config.whisperServerPath, Integer.toString(config.whisperServerPort));

		if (Objects.equals(settings, engineSettings) && status == Status.READY) {
			return;
		}

		closeEngine();
		engineSettings = settings;

		try {
			Files.createDirectories(modelsDirectory());
		} catch (IOException e) {
			LangMod.LOGGER.warn("Could not create {}", modelsDirectory(), e);
		}

		boolean useRunningServer = "server".equals(config.speechEngine) && config.whisperServerPath.isBlank();

		if (!useRunningServer && !Files.isRegularFile(model)) {
			setStatus(Status.MODEL_MISSING, "Whisper model not found. Download " + WhisperModels.fileName(config.modelSize)
					+ " from " + WhisperModels.DOWNLOAD_PAGE + " and put it in " + modelsDirectory().toAbsolutePath()
					+ " , then restart Minecraft (or run /langmod reload).");
			return;
		}

		setStatus(Status.LOADING, "Loading Whisper model " + model.getFileName() + "...");
		SpeechEngine candidate = "server".equals(config.speechEngine)
				? new WhisperServerEngine(config.whisperServerPath.isBlank() ? null : Path.of(config.whisperServerPath), model,
						config.whisperServerPort, threads, config.useGpu, LangModConfig.directory().resolve("whisper-server.log"),
						LangMod.LOGGER::info)
				: new WhisperJniEngine(model, threads, config.useGpu, line -> LangMod.LOGGER.debug("[whisper] {}", line.strip()));
		long start = System.nanoTime();

		try {
			candidate.load();
			engine = candidate;
			description = candidate.describe();
			LangMod.LOGGER.info("Speech recognition ready in {} ms: {}", (System.nanoTime() - start) / 1_000_000, description);
			setStatus(Status.READY, "Ready (" + description + ")");
		} catch (Throwable t) {
			LangMod.LOGGER.error("Could not start speech recognition", t);
			candidate.close();
			setStatus(Status.FAILED, "Speech recognition failed to start: " + t.getMessage());
		}
	}

	private void closeEngine() {
		if (engine != null) {
			try {
				engine.close();
			} catch (RuntimeException e) {
				LangMod.LOGGER.warn("Error closing speech engine", e);
			}

			engine = null;
		}
	}

	private void setStatus(Status status, String message) {
		this.status = status;
		this.statusMessage = message;

		if (status != Status.READY) {
			LangMod.LOGGER.warn("Speech recognition: {}", message);
		}

		for (Runnable listener : listeners) {
			listener.run();
		}
	}
}
