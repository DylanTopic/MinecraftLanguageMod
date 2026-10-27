package io.github.dylantopic.langmod.speech;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

import io.github.dylantopic.langmod.core.ItemDictionary;
import io.github.dylantopic.langmod.core.MatchMode;
import io.github.dylantopic.langmod.core.Matcher;
import io.github.dylantopic.langmod.core.PinyinEntry;
import io.github.dylantopic.langmod.core.speech.SpeechEngine;
import io.github.dylantopic.langmod.core.speech.WhisperJniEngine;
import io.github.dylantopic.langmod.core.speech.WhisperModels;
import io.github.dylantopic.langmod.core.text.CharReadings;
import io.github.dylantopic.langmod.core.text.ChineseConverter;
import io.github.dylantopic.langmod.core.text.TextNormalizer;

/**
 * Runs the mod's real speech pipeline (whisper-jni, OpenCC, pinyin) outside Minecraft on recorded
 * samples and prints what was heard. Used by .github/workflows/speech.yml on Linux and Windows.
 *
 * <p>Usage: {@code java SpeechCheck <ggml model> <samples dir> [threads]}, run from the repository root.
 */
public final class SpeechCheck {
	private static final Path ASSETS = Path.of("src/main/resources/assets/langmod");

	private SpeechCheck() {
	}

	record Sample(String file, String key, String spoken, String voice, String expect) {
	}

	public static void main(String[] args) throws Exception {
		Path model = Path.of(args[0]);
		Path samples = Path.of(args[1]);
		int threads = args.length > 2 ? Integer.parseInt(args[2]) : WhisperModels.autoThreads();

		TextNormalizer normalizer;
		CharReadings readings;
		ItemDictionary dictionary;

		try (Reader phrases = reader(ASSETS.resolve("opencc/TSPhrases.txt"));
				Reader characters = reader(ASSETS.resolve("opencc/TSCharacters.txt"));
				Reader chars = reader(ASSETS.resolve("pinyin/chars.tsv"));
				Reader items = reader(ASSETS.resolve("pinyin/items.json"))) {
			normalizer = new TextNormalizer(ChineseConverter.load(phrases, characters));
			readings = CharReadings.load(chars);
			dictionary = ItemDictionary.load(items, null, System.err::println);
		}

		System.out.printf(Locale.ROOT, "java %s on %s %s, %d cores%n", System.getProperty("java.version"),
				System.getProperty("os.name"), System.getProperty("os.arch"), Runtime.getRuntime().availableProcessors());
		long start = System.nanoTime();
		int asExpected = 0;
		int total = 0;
		List<String> surprises = new ArrayList<>();

		try (SpeechEngine engine = new WhisperJniEngine(model, threads, true, line -> { })) {
			engine.load();
			System.out.printf(Locale.ROOT, "Loaded %s in %d ms%n%n", engine.describe(), (System.nanoTime() - start) / 1_000_000);

			for (Sample sample : readManifest(samples.resolve("samples.tsv"))) {
				PinyinEntry expected = dictionary.get(sample.key()).orElseThrow(() -> new IllegalStateException("unknown key " + sample.key()));
				float[] audio = readWav(samples.resolve(sample.file() + ".wav"));

				for (boolean bias : new boolean[] {false, true}) {
					long t0 = System.nanoTime();
					String raw = engine.transcribe(audio, new SpeechEngine.Request("zh", bias ? expected.hanzi() : null));
					long ms = (System.nanoTime() - t0) / 1_000_000;
					String simplified = normalizer.normalize(raw);
					Matcher.Result strict = Matcher.match(expected, simplified, readings, MatchMode.STRICT, false);
					Matcher.Result toneless = Matcher.match(expected, simplified, readings, MatchMode.TONELESS, false);
					boolean ok = switch (sample.expect()) {
						case "pass" -> strict.pass();
						case "fail" -> !toneless.pass();
						case "fail-strict" -> !strict.pass();
						default -> throw new IllegalStateException("bad expectation " + sample.expect());
					};
					total++;
					asExpected += ok ? 1 : 0;
					String line = String.format(Locale.ROOT,
							"%-13s %-6s want %s %-14s said %-6s heard %-10s -> %-8s %-18s strict=%-4s toneless=%-4s %s %5d ms",
							sample.file(), bias ? "biased" : "plain", expected.hanzi(), expected.marks(), sample.spoken(),
							'"' + raw + '"', simplified.isEmpty() ? "(nothing)" : simplified, strict.heardPinyin(),
							strict.pass() ? "PASS" : "fail", toneless.pass() ? "PASS" : "fail", ok ? "as expected" : "UNEXPECTED", ms);
					System.out.println(line);

					if (!ok) {
						surprises.add(line);
					}
				}
			}
		}

		System.out.printf(Locale.ROOT, "%n%d of %d results as expected.%n", asExpected, total);
		surprises.forEach(line -> System.out.println("  " + line));
	}

	static List<Sample> readManifest(Path file) throws IOException {
		List<Sample> result = new ArrayList<>();

		for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
			if (line.isBlank() || line.startsWith("#")) {
				continue;
			}

			String[] f = line.split("\t");
			result.add(new Sample(f[0], f[1], f[2], f[3], f[4]));
		}

		return result;
	}

	static String pinyin(CharReadings readings, String text) {
		List<String> parts = new ArrayList<>();
		text.codePoints().forEach(cp -> parts.add(readings.of(cp).isEmpty() ? "?" : readings.of(cp).getFirst().marked()));
		return String.join(" ", parts);
	}

	/** Reads a 16 kHz mono 16-bit WAV file as floats. */
	static float[] readWav(Path file) throws IOException, UnsupportedAudioFileException {
		try (AudioInputStream in = AudioSystem.getAudioInputStream(file.toFile())) {
			AudioFormat format = in.getFormat();

			if (format.getSampleRate() != 16_000 || format.getChannels() != 1 || format.getSampleSizeInBits() != 16) {
				throw new IOException(file + " is not 16 kHz mono 16-bit: " + format);
			}

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

	private static Reader reader(Path path) throws IOException {
		return Files.newBufferedReader(path, StandardCharsets.UTF_8);
	}
}
