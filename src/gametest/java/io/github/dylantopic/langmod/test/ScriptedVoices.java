package io.github.dylantopic.langmod.test;

import java.util.List;

import io.github.dylantopic.langmod.core.audio.Wav;
import io.github.dylantopic.langmod.core.tts.TextToSpeech;
import io.github.dylantopic.langmod.core.tts.Voice;

/** Voices for game tests: one per language (none for Tagalog, as in Windows), each saying half a second of silence. */
final class ScriptedVoices implements TextToSpeech {
	@Override
	public List<Voice> voices() {
		return List.of(voice("zh-CN"), voice("es-MX"), voice("fr-CA"), voice("ko-KR"), voice("ru-RU"), voice("nl-NL"), voice("it-IT"),
				voice("ja-JP"), voice("hr-HR"), voice("pt-BR"), voice("vi-VN"), voice("de-DE"));
	}

	@Override
	public byte[] synthesize(Voice voice, String text, double rate) {
		return Wav.encode(new float[8_000], 16_000);
	}

	@Override
	public String describe() {
		return "scripted test voices";
	}

	@Override
	public void close() {
	}

	private static Voice voice(String locale) {
		return new Voice("test-" + locale, "Test " + locale, locale, "");
	}
}
