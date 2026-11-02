package io.github.dylantopic.langmod.core.tts;

import java.io.IOException;
import java.util.List;

/** Turns text into speech with the voices installed on the computer. Calls may block; not for the render thread. */
public interface TextToSpeech extends AutoCloseable {
	/** The voices installed (asked afresh, so a voice added meanwhile shows up). */
	List<Voice> voices() throws IOException;

	/**
	 * @param rate speed, 1.0 = normal
	 * @return the speech as a WAV file
	 */
	byte[] synthesize(Voice voice, String text, double rate) throws IOException;

	/** Where the voices come from, for messages ("Windows voices"). */
	String describe();

	@Override
	void close();
}
