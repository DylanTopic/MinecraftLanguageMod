package io.github.dylantopic.langmod.core.lang;

import java.util.Map;
import java.util.Optional;

import io.github.dylantopic.langmod.core.MatchMode;

/**
 * Everything language-specific about a challenge: the item names, how a transcription is
 * cleaned up, how it is compared with the name, and how pronunciation is shown.
 */
public interface LanguagePack {
	Language language();

	/** The item's name in this language, by translation key. */
	Optional<NameEntry> name(String translationKey);

	int nameCount();

	/** Names taken from the user's override file. */
	int overrideCount();

	/** Whisper's raw output cleaned up for comparing and showing (punctuation, case, script). */
	String normalize(String transcript);

	/** Pinyin or romanization of a normalized transcription, empty if the language has none. */
	String pronounce(String normalized);

	/** Text Whisper is known to invent for silence or noise ("thanks for watching", subtitle credits). */
	boolean isNoise(String normalized, NameEntry expected);

	/**
	 * @param normalized a transcription from {@link #normalize}
	 * @param longName the name is over the configured length and gets one error allowed
	 */
	CheckResult check(NameEntry expected, String normalized, MatchMode mode, boolean longName);

	/** Where a name goes in an alphabetical list (Mandarin and Japanese are sorted by pronunciation). */
	default String sortKey(NameEntry entry) {
		return entry.text();
	}

	/**
	 * The pack of any language but Mandarin, which also needs pinyin tables.
	 *
	 * @param kanaReadings Japanese only: name -> its reading in hiragana (assets/langmod/names/ja_jp_kana.json)
	 */
	static LanguagePack forNames(Language language, NameDictionary names, Map<String, String> kanaReadings) {
		return switch (language) {
			case MANDARIN -> throw new IllegalArgumentException("Mandarin has its own pack");
			case KOREAN -> new KoreanPack(names);
			case JAPANESE -> new JapanesePack(names, kanaReadings);
			default -> new WordPack(language, WordRules.of(language), names);
		};
	}
}
