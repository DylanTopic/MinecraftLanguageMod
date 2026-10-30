package io.github.dylantopic.langmod.core.lang;

/**
 * Hangul syllable blocks: splitting them into their initial consonant, vowel and final consonant
 * (jamo) indexes, and a "sounds the same" key used to accept spellings that are pronounced alike.
 */
public final class Hangul {
	private static final int FIRST = 0xAC00;
	private static final int LAST = 0xD7A3;
	private static final int VOWELS = 21;
	private static final int FINALS = 28;

	/** Final consonant index -> the final that is actually pronounced (only 7 are distinguished at the end of a syllable). */
	private static final int[] FINAL_SOUND = {
			0, 1, 1, 1, 4, 4, 4, 7, 8, 1, 16, 8, 8, 8, 17, 8, 16, 17, 17, 7, 7, 21, 7, 7, 1, 7, 17, 7
	};

	private Hangul() {
	}

	public static boolean isSyllable(int cp) {
		return cp >= FIRST && cp <= LAST;
	}

	/** Initial consonant, 0-18 (ㄱ ㄲ ㄴ ㄷ ㄸ ㄹ ㅁ ㅂ ㅃ ㅅ ㅆ ㅇ ㅈ ㅉ ㅊ ㅋ ㅌ ㅍ ㅎ). */
	static int initial(int syllable) {
		return (syllable - FIRST) / (VOWELS * FINALS);
	}

	/** Vowel, 0-20 (ㅏ ㅐ ㅑ ㅒ ㅓ ㅔ ㅕ ㅖ ㅗ ㅘ ㅙ ㅚ ㅛ ㅜ ㅝ ㅞ ㅟ ㅠ ㅡ ㅢ ㅣ). */
	static int vowel(int syllable) {
		return (syllable - FIRST) % (VOWELS * FINALS) / FINALS;
	}

	/** Final consonant, 0 (none) - 27. */
	static int last(int syllable) {
		return (syllable - FIRST) % FINALS;
	}

	static int compose(int initial, int vowel, int last) {
		return FIRST + (initial * VOWELS + vowel) * FINALS + last;
	}

	/**
	 * The syllable as it sounds on its own: ㅐ/ㅔ, ㅒ/ㅖ and ㅙ/ㅚ/ㅞ are merged (most speakers
	 * pronounce them alike) and a final consonant is reduced to the one actually pronounced
	 * (옷, 옻 and 옫 all end in [t]). Non-syllables are returned unchanged.
	 */
	public static int soundKey(int cp) {
		if (!isSyllable(cp)) {
			return cp;
		}

		int vowel = switch (vowel(cp)) {
			case 1 -> 5;
			case 3 -> 7;
			case 10, 11 -> 15;
			default -> vowel(cp);
		};
		return compose(initial(cp), vowel, FINAL_SOUND[last(cp)]);
	}
}
