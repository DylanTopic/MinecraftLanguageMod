package io.github.dylantopic.langmod.test;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import io.github.dylantopic.langmod.core.speech.SpeechEngine;

/** A speech engine for game tests: returns queued answers instead of running Whisper. */
final class ScriptedSpeechEngine implements SpeechEngine {
	private final Queue<String> answers = new ConcurrentLinkedQueue<>();

	void enqueue(String answer) {
		answers.add(answer);
	}

	@Override
	public void load() {
	}

	@Override
	public String transcribe(float[] samples, Request request) {
		String answer = answers.poll();
		return answer == null ? "" : answer;
	}

	@Override
	public String describe() {
		return "scripted test engine";
	}

	@Override
	public void close() {
	}
}
