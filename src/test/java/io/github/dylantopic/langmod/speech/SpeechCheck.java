package io.github.dylantopic.langmod.speech;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
 * <p>Usage: {@code java SpeechCheck <ggml model> <samples dir> [threads|auto] [variants] [max samples]},
 * run from the repository root. Variants are a comma-separated list of the names in {@link #VARIANTS}.
 */
public final class SpeechCheck {
	private static final Path ASSETS = Path.of("src/main/resources/assets/langmod");
	private static final String MANDARIN_HINT = "以下是普通话的句子。";
	private static final String MINECRAFT_HINT = "我的世界物品：";
	/** Ways of calling Whisper to compare. "biased" passes the expected word as the prompt. */
	private static final List<Variant> VARIANTS = List.of(
			new Variant("plain", false, null, 0),
			new Variant("hint", false, MANDARIN_HINT, 0),
			new Variant("mc", false, MINECRAFT_HINT, 0),
			new Variant("biased", true, null, 0),
			new Variant("fit", false, null, -1),
			new Variant("fit-hint", false, MANDARIN_HINT, -1),
			new Variant("fit-mc", false, MINECRAFT_HINT, -1),
			new Variant("fit-biased", true, null, -1),
			new Variant("ctx512", false, null, 512),
			new Variant("ctx768", false, null, 768),
			new Variant("hint512", false, MANDARIN_HINT, 512),
			new Variant("mc512", false, MINECRAFT_HINT, 512),
			new Variant("mc768", false, MINECRAFT_HINT, 768),
			new Variant("biased512", true, null, 512));

	private SpeechCheck() {
	}

	record Sample(String file, String key, String spoken, String voice, String expect) {
	}

	record Variant(String name, boolean biased, String prompt, int audioContext) {
	}

	public static void main(String[] args) throws Exception {
		Path model = Path.of(args[0]);
		Path samples = Path.of(args[1]);
		int threads = args.length > 2 && !args[2].equals("auto") ? Integer.parseInt(args[2]) : WhisperModels.autoThreads();
		List<String> wanted = List.of((args.length > 3 ? args[3] : "plain,biased").split(","));
		List<Variant> variants = VARIANTS.stream().filter(v -> wanted.contains(v.name())).toList();
		int maxSamples = args.length > 4 ? Integer.parseInt(args[4]) : Integer.MAX_VALUE;

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
		List<Sample> manifest = readManifest(samples.resolve("samples.tsv"));
		manifest = manifest.subList(0, Math.min(maxSamples, manifest.size()));
		Map<String, int[]> perVariant = new LinkedHashMap<>(); // name -> {as expected, total, total ms}
		List<String> surprises = new ArrayList<>();

		try (SpeechEngine engine = new WhisperJniEngine(model, threads, true, line -> { })) {
			engine.load();
			System.out.printf(Locale.ROOT, "Loaded %s in %d ms%n%n", engine.describe(), (System.nanoTime() - start) / 1_000_000);

			for (Variant variant : variants) {
				int[] stats = perVariant.computeIfAbsent(variant.name(), name -> new int[3]);

				for (Sample sample : manifest) {
					PinyinEntry expected = dictionary.get(sample.key()).orElseThrow(() -> new IllegalStateException("unknown key " + sample.key()));
					float[] audio = readWav(samples.resolve(sample.file() + ".wav"));
					String prompt = variant.biased() ? expected.hanzi() : variant.prompt();
					int audioContext = WhisperModels.audioContext(audio.length, variant.audioContext());
					long t0 = System.nanoTime();
					String raw = engine.transcribe(audio, new SpeechEngine.Request("zh", prompt, audioContext));
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
					stats[0] += ok ? 1 : 0;
					stats[1]++;
					stats[2] += (int) ms;
					String line = String.format(Locale.ROOT,
							"%-13s %-10s want %s %-14s said %-6s heard %-10s -> %-8s %-18s strict=%-4s toneless=%-4s %s %5d ms (ctx %d)",
							sample.file(), variant.name(), expected.hanzi(), expected.marks(), sample.spoken(),
							'"' + raw + '"', simplified.isEmpty() ? "(nothing)" : simplified, strict.heardPinyin(),
							strict.pass() ? "PASS" : "fail", toneless.pass() ? "PASS" : "fail", ok ? "as expected" : "UNEXPECTED", ms,
							audioContext);
					System.out.println(line);

					if (!ok) {
						surprises.add(line);
					}
				}
			}
		}

		System.out.printf(Locale.ROOT, "%nvariant     as expected   avg time%n");
		perVariant.forEach((name, stats) -> System.out.printf(Locale.ROOT, "%-10s  %3d of %-3d   %6d ms%n",
				name, stats[0], stats[1], stats[2] / Math.max(1, stats[1])));
		System.out.printf(Locale.ROOT, "%nUnexpected results:%n");
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
