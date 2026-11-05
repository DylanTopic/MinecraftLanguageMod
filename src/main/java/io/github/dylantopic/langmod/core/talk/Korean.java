package io.github.dylantopic.langmod.core.talk;

import io.github.dylantopic.langmod.core.lang.Hangul;

/** Korean endings that depend on whether the word before them ends in a consonant (받침). */
final class Korean {
	private Korean() {
	}

	/** Whether the last syllable ends in a consonant; false for anything that is not Hangul. */
	static boolean endsInConsonant(String word) {
		if (word.isEmpty()) {
			return false;
		}

		int last = word.codePointBefore(word.length());
		return Hangul.isSyllable(last) && (last - 0xAC00) % 28 != 0;
	}

	/** "Is" after a noun: 농부예요, 석공이에요; 입니다 after a name in Latin letters, whose sound is unknown. */
	static String copula(String noun) {
		if (noun.isEmpty() || !Hangul.isSyllable(noun.codePointBefore(noun.length()))) {
			return "입니다";
		}

		return endsInConsonant(noun) ? "이에요" : "예요";
	}

	/** The topic particle: 파란색은, 보라는. */
	static String topic(String noun) {
		return endsInConsonant(noun) ? "은" : "는";
	}
}
