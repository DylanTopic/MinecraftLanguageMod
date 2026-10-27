package io.github.dylantopic.langmod.core.speech;

/**
 * A speech-to-text backend. Implementations are used from a single background thread: first
 * {@link #load()}, then any number of {@link #transcribe} calls, then {@link #close()}.
 *
 * <p>Two local whisper.cpp engines exist ({@link WhisperJniEngine}, {@link WhisperServerEngine});
 * a cloud service could implement the same interface.
 */
public interface SpeechEngine extends AutoCloseable {
	/** Prepares the engine (loads the model). Blocking; throws if the engine cannot be used. */
	void load() throws Exception;

	/**
	 * Transcribes one utterance. Blocking.
	 *
	 * @param samples 16 kHz mono audio in [-1, 1]
	 * @return the raw text as produced by the engine
	 */
	String transcribe(float[] samples, Request request) throws Exception;

	/** Human-readable summary, e.g. {@code whisper.cpp (JNI) ggml-small.bin, 6 threads, CPU}. */
	String describe();

	@Override
	void close();

	/**
	 * @param language ISO code; always "zh" here (auto-detection misfires on single words)
	 * @param initialPrompt text to prime Whisper with, or null for none
	 * @param audioContext whisper.cpp's audio_ctx: encoder frames to process (50 per second of audio),
	 *     or 0 for the model's full 30 second window. See {@link WhisperModels#audioContext}.
	 */
	record Request(String language, String initialPrompt, int audioContext) {
		public Request(String language, String initialPrompt) {
			this(language, initialPrompt, 0);
		}
	}
}
