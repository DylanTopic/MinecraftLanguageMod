package io.github.dylantopic.langmod.speech;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
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
import io.github.dylantopic.langmod.core.lang.CheckResult;
import io.github.dylantopic.langmod.core.lang.Language;
import io.github.dylantopic.langmod.core.lang.LanguagePack;
import io.github.dylantopic.langmod.core.lang.MandarinPack;
import io.github.dylantopic.langmod.core.lang.NameDictionary;
import io.github.dylantopic.langmod.core.lang.NameEntry;
import io.github.dylantopic.langmod.core.speech.SpeechEngine;
import io.github.dylantopic.langmod.core.speech.WhisperJniEngine;
import io.github.dylantopic.langmod.core.speech.WhisperModels;
import io.github.dylantopic.langmod.core.text.CharReadings;
import io.github.dylantopic.langmod.core.text.ChineseConverter;
import io.github.dylantopic.langmod.core.text.TextNormalizer;

/**
 * Runs the mod's real speech pipeline (whisper-jni, then each language's normalizing and
 * matching) outside Minecraft on recorded samples and prints what was heard. Used by
 * .github/workflows/speech.yml on Linux and Windows.
 *
 * <p>Usage: {@code java SpeechCheck <ggml model> <samples dir> [threads|auto] [variants] [max samples]},
 * run from the repository root. Variants are a comma-separated list of the names in {@link #VARIANTS}.
 */
public final class SpeechCheck {
	private static final Path ASSETS = Path.of("src/main/resources/assets/langmod");
	private static final String MANDARIN_HINT = "以下是普通话的句子。";
	/**
	 * Ways of calling Whisper to compare: which prompt (none, the Mandarin sentence hint, the
	 * language's generic "Minecraft items:" prompt, or the expected name) and audio context.
	 */
	private static final List<Variant> VARIANTS = List.of(
			new Variant("plain", Prompt.NONE, 0),
			new Variant("hint", Prompt.MANDARIN_HINT, 0),
			new Variant("mc", Prompt.LANGUAGE, 0),
			new Variant("biased", Prompt.EXPECTED, 0),
			new Variant("fit", Prompt.NONE, -1),
			new Variant("fit-hint", Prompt.MANDARIN_HINT, -1),
			new Variant("fit-mc", Prompt.LANGUAGE, -1),
			new Variant("fit-biased", Prompt.EXPECTED, -1),
			new Variant("ctx512", Prompt.NONE, 512),
			new Variant("ctx768", Prompt.NONE, 768),
			new Variant("hint512", Prompt.MANDARIN_HINT, 512),
			new Variant("mc512", Prompt.LANGUAGE, 512),
			new Variant("mc768", Prompt.LANGUAGE, 768),
			new Variant("biased512", Prompt.EXPECTED, 512));

	private SpeechCheck() {
	}

	enum Prompt {
		NONE, MANDARIN_HINT, LANGUAGE, EXPECTED
	}

	record Sample(String file, String key, String spoken, String voice, String expect, Language language) {
	}

	record Variant(String name, Prompt prompt, int audioContext) {
		String promptFor(Language language, NameEntry expected) {
			return switch (prompt) {
				case NONE -> null;
				case MANDARIN_HINT -> language == Language.MANDARIN ? MANDARIN_HINT : null;
				case LANGUAGE -> language.defaultPrompt();
				case EXPECTED -> expected.text();
			};
		}
	}

	public static void main(String[] args) throws Exception {
		Path model = Path.of(args[0]);
		Path samples = Path.of(args[1]);
		int threads = args.length > 2 && !args[2].equals("auto") ? Integer.parseInt(args[2]) : WhisperModels.autoThreads();
		List<String> wanted = List.of((args.length > 3 ? args[3] : "plain,biased").split(","));
		List<Variant> variants = VARIANTS.stream().filter(v -> wanted.contains(v.name())).toList();
		int maxSamples = args.length > 4 ? Integer.parseInt(args[4]) : Integer.MAX_VALUE;
		List<String> languages = args.length > 5 && !args[5].equals("all") ? List.of(args[5].split(",")) : null;
		Map<Language, LanguagePack> packs = loadPacks();

		System.out.printf(Locale.ROOT, "java %s on %s %s, %d cores%n", System.getProperty("java.version"),
				System.getProperty("os.name"), System.getProperty("os.arch"), Runtime.getRuntime().availableProcessors());
		long start = System.nanoTime();
		List<Sample> manifest = readManifest(samples.resolve("samples.tsv"));
		manifest = manifest.stream().filter(sample -> languages == null || languages.contains(sample.language().id())).toList();
		manifest = manifest.subList(0, Math.min(maxSamples, manifest.size()));
		Map<String, int[]> stats = new LinkedHashMap<>(); // "variant language" -> {as expected, total, total ms}
		List<String> surprises = new ArrayList<>();

		try (SpeechEngine engine = new WhisperJniEngine(model, threads, true, line -> { })) {
			engine.load();
			System.out.printf(Locale.ROOT, "Loaded %s in %d ms%n%n", engine.describe(), (System.nanoTime() - start) / 1_000_000);

			for (Variant variant : variants) {
				for (Sample sample : manifest) {
					LanguagePack pack = packs.get(sample.language());
					NameEntry expected = pack.name(sample.key()).orElseThrow(() -> new IllegalStateException("unknown key " + sample.key()));
					float[] audio = readWav(samples.resolve(sample.file() + ".wav"));
					int audioContext = WhisperModels.audioContext(audio.length, variant.audioContext());
					long t0 = System.nanoTime();
					String raw = engine.transcribe(audio, new SpeechEngine.Request(sample.language().whisperCode(),
							variant.promptFor(sample.language(), expected), audioContext));
					long ms = (System.nanoTime() - t0) / 1_000_000;
					String heard = pack.normalize(raw);
					CheckResult strict = pack.check(expected, heard, MatchMode.STRICT, false);
					CheckResult toneless = pack.check(expected, heard, MatchMode.TONELESS, false);
					boolean ok = switch (sample.expect()) {
						case "pass" -> strict.pass();
						case "fail" -> !toneless.pass();
						case "fail-strict" -> !strict.pass();
						default -> throw new IllegalStateException("bad expectation " + sample.expect());
					};
					int[] counts = stats.computeIfAbsent(variant.name() + " " + sample.language().id(), key -> new int[3]);
					counts[0] += ok ? 1 : 0;
					counts[1]++;
					counts[2] += (int) ms;
					String line = String.format(Locale.ROOT,
							"%-18s %-10s want %s %s  said %s  heard %s -> %s %s  strict=%s toneless=%s %s %d ms (ctx %d)",
							sample.file(), variant.name(), expected.text(), expected.pronunciation(), sample.spoken(),
							'"' + raw + '"', heard.isEmpty() ? "(nothing)" : heard, strict.heardPronunciation(),
							strict.pass() ? "PASS" : "fail", toneless.pass() ? "PASS" : "fail", ok ? "as expected" : "UNEXPECTED", ms,
							audioContext);
					System.out.println(line);

					if (!ok) {
						surprises.add(line);
					}
				}
			}
		}

		System.out.printf(Locale.ROOT, "%nvariant    language  as expected   avg time%n");
		stats.forEach((key, counts) -> System.out.printf(Locale.ROOT, "%-19s  %3d of %-3d   %6d ms%n",
				key, counts[0], counts[1], counts[2] / Math.max(1, counts[1])));
		System.out.printf(Locale.ROOT, "%nUnexpected results:%n");
		surprises.forEach(line -> System.out.println("  " + line));
	}

	/** The same language packs the mod uses, read from the source tree. */
	static Map<Language, LanguagePack> loadPacks() throws IOException {
		Map<Language, LanguagePack> packs = new EnumMap<>(Language.class);

		try (Reader phrases = reader(ASSETS.resolve("opencc/TSPhrases.txt"));
				Reader characters = reader(ASSETS.resolve("opencc/TSCharacters.txt"));
				Reader chars = reader(ASSETS.resolve("pinyin/chars.tsv"));
				Reader items = reader(ASSETS.resolve("pinyin/items.json"))) {
			packs.put(Language.MANDARIN, new MandarinPack(ItemDictionary.load(items, null, System.err::println),
					new TextNormalizer(ChineseConverter.load(phrases, characters)), CharReadings.load(chars)));
		}

		Map<String, String> kana;

		try (Reader readings = reader(ASSETS.resolve("names/ja_jp_kana.json"))) {
			kana = NameDictionary.load(readings, null, System.err::println).entries();
		}

		for (Language language : Language.values()) {
			if (language != Language.MANDARIN) {
				try (Reader names = reader(ASSETS.resolve("names/" + language.id() + ".json"))) {
					packs.put(language, LanguagePack.forNames(language, NameDictionary.load(names, null, System.err::println), kana));
				}
			}
		}

		return packs;
	}

	static List<Sample> readManifest(Path file) throws IOException {
		List<Sample> result = new ArrayList<>();

		for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
			if (line.isBlank() || line.startsWith("#")) {
				continue;
			}

			String[] f = line.split("\t");
			Language language = f.length > 5 ? Language.byId(f[5]).orElseThrow(() -> new IllegalStateException("unknown language " + f[5]))
					: Language.MANDARIN;
			result.add(new Sample(f[0], f[1], f[2], f[3], f[4], language));
		}

		return result;
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
