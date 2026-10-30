package io.github.dylantopic.langmod.core.lang;

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
}
