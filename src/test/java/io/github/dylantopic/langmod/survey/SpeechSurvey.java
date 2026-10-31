package io.github.dylantopic.langmod.survey;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

import io.github.dylantopic.langmod.core.speech.SpeechEngine;
import io.github.dylantopic.langmod.core.speech.WhisperJniEngine;
import io.github.dylantopic.langmod.core.speech.WhisperModels;

/**
 * Research tool: how well Whisper hears item names in languages LangMod does not support yet.
 * Transcribes .github/survey/survey.tsv without a prompt and with the expected name as prompt,
 * and counts transcripts equal to the name once case, accents, punctuation and kana type are ignored.
 *
 * <p>Usage: SpeechSurvey model.bin samples_dir threads
 */
public final class SpeechSurvey {
	private SpeechSurvey() {
	}

	public static void main(String[] args) throws Exception {
		Path model = Path.of(args[0]);
		Path samples = Path.of(args[1]);
		int threads = Integer.parseInt(args[2]);
		Map<String, int[]> stats = new LinkedHashMap<>(); // language -> {plain ok, biased ok, total}

		try (SpeechEngine engine = new WhisperJniEngine(model, threads, true, line -> { })) {
			engine.load();

			for (String line : Files.readAllLines(samples.resolve("samples.tsv"), StandardCharsets.UTF_8)) {
				if (line.isBlank() || line.startsWith("#")) {
					continue;
				}

				String[] f = line.split("\t");
				String file = f[0];
				String text = f[2];
				String code = f[5];
				float[] audio = readWav(samples.resolve(file + ".wav"));
				int context = WhisperModels.audioContext(audio.length, 512);
				String plain = engine.transcribe(audio, new SpeechEngine.Request(code, null, context)).trim();
				String biased = engine.transcribe(audio, new SpeechEngine.Request(code, text, context)).trim();
				boolean plainOk = fold(plain).equals(fold(text));
				boolean biasedOk = fold(biased).equals(fold(text));
				int[] counts = stats.computeIfAbsent(code, key -> new int[3]);
				counts[0] += plainOk ? 1 : 0;
				counts[1] += biasedOk ? 1 : 0;
				counts[2]++;
				System.out.printf(Locale.ROOT, "%-20s want %-24s plain %-28s %s   biased %-28s %s%n", file, text, '"' + plain + '"',
						plainOk ? "OK" : "--", '"' + biased + '"', biasedOk ? "OK" : "--");
			}
		}

		System.out.println();
		System.out.println("language  same as the name: no prompt / expected name as prompt");

		for (Map.Entry<String, int[]> entry : stats.entrySet()) {
			int[] c = entry.getValue();
			System.out.printf(Locale.ROOT, "%-8s  %d of %d / %d of %d%n", entry.getKey(), c[0], c[2], c[1], c[2]);
		}
	}

	/** Lowercase letters and digits only, without accents, katakana as hiragana. */
	static String fold(String text) {
		String s = Normalizer.normalize(text, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
		s = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
		StringBuilder out = new StringBuilder();

		for (int cp : s.codePoints().toArray()) {
			if (cp >= 0x30A1 && cp <= 0x30F6) {
				out.appendCodePoint(cp - 0x60); // katakana -> hiragana
			} else if (Character.isLetterOrDigit(cp) || cp == 0x30FC) {
				out.appendCodePoint(cp);
			}
		}

		return Normalizer.normalize(out.toString(), Normalizer.Form.NFC);
	}

	static float[] readWav(Path file) throws IOException, UnsupportedAudioFileException {
		try (AudioInputStream in = AudioSystem.getAudioInputStream(file.toFile())) {
			AudioFormat format = in.getFormat();
			byte[] bytes = in.readAllBytes();
			boolean bigEndian = format.isBigEndian();
			float[] samples = new float[bytes.length / 2];

			for (int i = 0; i < samples.length; i++) {
				int lo = bytes[2 * i + (bigEndian ? 1 : 0)] & 0xFF;
				int hi = bytes[2 * i + (bigEndian ? 0 : 1)];
				samples[i] = (short) ((hi << 8) | lo) / 32768f;
			}

			return samples;
		}
	}
}
