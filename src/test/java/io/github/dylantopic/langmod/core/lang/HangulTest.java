package io.github.dylantopic.langmod.core.lang;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class HangulTest {
	@Test
	void splitsAndJoinsSyllables() {
		int gwa = '과';
		assertTrue(Hangul.isSyllable(gwa));
		assertFalse(Hangul.isSyllable('a'));
		assertEquals('과', Hangul.compose(Hangul.initial(gwa), Hangul.vowel(gwa), Hangul.last(gwa)));
		assertEquals(0, Hangul.initial(gwa)); // ㄱ
		assertEquals(9, Hangul.vowel(gwa)); // ㅘ
		assertEquals(0, Hangul.last('과'));
		assertEquals(9, Hangul.last('흙')); // ㄺ
	}

	@Test
	void syllablesThatSoundAlikeShareAKey() {
		assertEquals(Hangul.soundKey('개'), Hangul.soundKey('게')); // ㅐ = ㅔ
		assertEquals(Hangul.soundKey('왜'), Hangul.soundKey('웨')); // ㅙ = ㅞ
		assertEquals(Hangul.soundKey('옷'), Hangul.soundKey('옫')); // final ㅅ sounds like ㄷ
		assertEquals(Hangul.soundKey('밖'), Hangul.soundKey('박')); // ㄲ final sounds like ㄱ
		assertNotEquals(Hangul.soundKey('사'), Hangul.soundKey('자'));
		assertNotEquals(Hangul.soundKey('과'), Hangul.soundKey('가'));
	}
}
