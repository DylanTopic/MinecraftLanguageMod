package io.github.dylantopic.langmod.core.tts;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import io.github.dylantopic.langmod.core.lang.Language;

/** Which installed voice says the names of each language. */
public final class VoiceChooser {
	private VoiceChooser() {
	}

	/**
	 * Locales whose voices can say the language, best first: "zh-CN" is that locale only, "es" any
	 * Spanish one. A language without voices of its own borrows a close one: Latin is read the church
	 * way by an Italian voice, Bosnian by a Croatian or Serbian one (same sounds, same spelling).
	 * Mandarin never gets a Hong Kong voice, which speaks Cantonese.
	 */
	static List<String> locales(Language language) {
		return switch (language) {
			case MANDARIN -> List.of("zh-CN", "zh-SG", "zh-TW");
			case SPANISH -> List.of("es-MX", "es-US", "es");
			case FRENCH -> List.of("fr-CA", "fr");
			case KOREAN -> List.of("ko");
			case RUSSIAN -> List.of("ru");
			case DUTCH -> List.of("nl-NL", "nl");
			case LATIN -> List.of("la", "it-IT", "it");
			case FILIPINO -> List.of("fil", "tl");
			case JAPANESE -> List.of("ja");
			case BOSNIAN -> List.of("bs", "hr", "sr-Latn", "sr");
			case PORTUGUESE -> List.of("pt-BR", "pt");
			case VIETNAMESE -> List.of("vi");
			case GERMAN -> List.of("de-DE", "de");
			case ENGLISH -> List.of("en-US", "en");
		};
	}

	/** The installed voices that can say the language, best first. */
	public static List<Voice> candidates(Language language, List<Voice> voices) {
		List<Voice> found = new ArrayList<>();

		for (String locale : locales(language)) {
			for (Voice voice : voices) {
				if (matches(voice.locale(), locale) && !found.contains(voice)) {
					found.add(voice);
				}
			}
		}

		return found;
	}

	/**
	 * @param preferred (part of) the name of the voice chosen in the settings, or empty for the best
	 *     candidate; a voice that is no longer installed is ignored
	 */
	public static Optional<Voice> choose(Language language, List<Voice> voices, String preferred) {
		if (preferred != null && !preferred.isBlank()) {
			String wanted = preferred.trim().toLowerCase(Locale.ROOT);

			for (Voice voice : voices) {
				if (voice.name().toLowerCase(Locale.ROOT).contains(wanted)) {
					return Optional.of(voice);
				}
			}
		}

		return candidates(language, voices).stream().findFirst();
	}

	/** Whether a voice's locale is the one wanted: "es" matches es-MX and es-ES, "sr-Latn" matches sr-Latn-RS. */
	static boolean matches(String voiceLocale, String wanted) {
		String locale = voiceLocale.replace('_', '-').toLowerCase(Locale.ROOT);
		String prefix = wanted.toLowerCase(Locale.ROOT);
		return locale.equals(prefix) || locale.startsWith(prefix + "-");
	}
}
