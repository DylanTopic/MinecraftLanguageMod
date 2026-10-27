package io.github.dylantopic.langmod.core;

import java.util.Locale;
import java.util.Optional;

/** How closely the transcription's pinyin must match the expected pinyin. */
public enum MatchMode {
	/** Every syllable and every tone must match. */
	STRICT,
	/** Every syllable must match; tones are ignored. */
	TONELESS,
	/** Like strict, but names of 4+ syllables may have one wrong, missing or extra syllable. */
	LENIENT;

	public static Optional<MatchMode> fromString(String value) {
		if (value == null) {
			return Optional.empty();
		}

		try {
			return Optional.of(valueOf(value.trim().toUpperCase(Locale.ROOT)));
		} catch (IllegalArgumentException e) {
			return Optional.empty();
		}
	}

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}
}
