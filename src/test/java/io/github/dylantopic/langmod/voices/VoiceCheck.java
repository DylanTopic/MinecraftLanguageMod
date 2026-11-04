package io.github.dylantopic.langmod.voices;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import io.github.dylantopic.langmod.core.audio.Wav;
import io.github.dylantopic.langmod.core.lang.Language;
import io.github.dylantopic.langmod.core.tts.Voice;
import io.github.dylantopic.langmod.core.tts.VoiceChooser;
import io.github.dylantopic.langmod.core.tts.WindowsTts;

/**
 * Checks on Windows that the mod can use the voices installed there (.github/workflows/voices.yml):
 * starts the PowerShell helper, lists the voices, has the voice of every language that has one say
 * its word for apple, and checks that each answer is a WAV file of a plausible length. Also checks
 * that a failed request leaves the helper working. Exits with 1 if something that should work fails.
 *
 * <p>Usage: {@code VoiceCheck <directory for the script and the WAV files>}
 */
public final class VoiceCheck {
	private static final Map<Language, String> APPLE = apples();
	private static int failures;

	private VoiceCheck() {
	}

	public static void main(String[] args) throws IOException {
		Path out = Path.of(args.length > 0 ? args[0] : "voices-out");
		Files.createDirectories(out);
		Path powershell = WindowsTts.findPowerShell().orElseThrow(() -> new IllegalStateException("Windows PowerShell not found"));

		try (WindowsTts tts = new WindowsTts(powershell, out)) {
			long start = System.nanoTime();
			List<Voice> voices = tts.voices();
			System.out.printf(Locale.ROOT, "%d voices, listed in %d ms (including starting PowerShell):%n", voices.size(), millis(start));
			voices.forEach(voice -> System.out.printf(Locale.ROOT, "  %-28s %-10s %s%n", voice.name(), voice.locale(), voice.gender()));

			if (voices.isEmpty()) {
				System.out.println("No voices installed: nothing can be said.");
				System.exit(1);
			}

			System.out.println();
			System.out.println("Each language's voice says its word for apple:");

			for (Language language : Language.values()) {
				Optional<Voice> voice = VoiceChooser.choose(language, voices, "");

				if (voice.isEmpty()) {
					System.out.printf(Locale.ROOT, "  %-6s no voice%n", language.id());
					continue;
				}

				say(tts, voice.get(), APPLE.get(language), out.resolve(language.id() + ".wav"), language.id());
			}

			System.out.println();
			Voice first = voices.getFirst();
			say(tts, first, "Hello from LangMod.", out.resolve("hello.wav"), "first voice");
			say(tts, first, "Hello from LangMod.", out.resolve("hello-slow.wav"), "again, slower");

			try {
				tts.synthesize(new Voice("no such voice", "Nobody", "xx-XX", ""), "test", 1.0);
				fail("a voice that does not exist was accepted");
			} catch (IOException e) {
				System.out.println("Unknown voice refused as expected: " + e.getMessage());
			}

			say(tts, first, "Still working.", out.resolve("after-error.wav"), "after the error");
		}

		System.out.println(failures == 0 ? "All voice checks passed." : failures + " voice check(s) failed.");
		System.exit(failures == 0 ? 0 : 1);
	}

	private static void say(WindowsTts tts, Voice voice, String text, Path file, String label) {
		long start = System.nanoTime();

		try {
			byte[] wav = tts.synthesize(voice, text, label.contains("slower") ? 0.8 : 1.0);
			long took = millis(start);
			Files.write(file, wav);
			Wav.Audio audio = Wav.decode(wav);
			System.out.printf(Locale.ROOT, "  %-16s %-28s \"%s\": %d ms of sound (%d Hz, %d bit, %d channel%s), made in %d ms%n", label,
					voice.describe(), text, audio.millis(), audio.sampleRate(), audio.bitsPerSample(), audio.channels(),
					audio.channels() == 1 ? "" : "s", took);

			if (audio.millis() < 150 || audio.millis() > 8_000) {
				fail(label + ": implausible length " + audio.millis() + " ms");
			}
		} catch (IOException | IllegalArgumentException e) {
			fail(label + " (" + voice.describe() + "): " + e.getMessage());
		}
	}

	private static void fail(String message) {
		failures++;
		System.out.println("FAILED: " + message);
	}

	private static long millis(long start) {
		return (System.nanoTime() - start) / 1_000_000;
	}

	private static Map<Language, String> apples() {
		Map<Language, String> apples = new EnumMap<>(Language.class);
		apples.put(Language.MANDARIN, "苹果");
		apples.put(Language.SPANISH, "Manzana");
		apples.put(Language.FRENCH, "Pomme");
		apples.put(Language.KOREAN, "사과");
		apples.put(Language.RUSSIAN, "Яблоко");
		apples.put(Language.DUTCH, "Appel");
		apples.put(Language.LATIN, "Malum");
		apples.put(Language.FILIPINO, "Mansanas");
		apples.put(Language.JAPANESE, "リンゴ");
		apples.put(Language.BOSNIAN, "Jabuka");
		apples.put(Language.PORTUGUESE, "Maçã");
		apples.put(Language.VIETNAMESE, "Táo");
		apples.put(Language.GERMAN, "Apfel");
		apples.put(Language.ENGLISH, "Apple");
		return apples;
	}
}
