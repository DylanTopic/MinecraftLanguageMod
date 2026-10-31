package io.github.dylantopic.langmod.core.lang;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RomanizersTest {
	@Test
	void russian() {
		assertEquals("Yabloko", RussianRomanizer.romanize("Яблоко"));
		assertEquals("Zheleznyy slitok", RussianRomanizer.romanize("Железный слиток"));
		assertEquals("Zelyonaya sherst", RussianRomanizer.romanize("Зелёная шерсть"));
		assertEquals("Almaznaya kirka", RussianRomanizer.romanize("Алмазная кирка"));
		assertEquals("Yel", RussianRomanizer.romanize("Ель"));
		assertEquals("Khleb", RussianRomanizer.romanize("Хлеб"));
		assertEquals("Shchit", RussianRomanizer.romanize("Щит"));
	}

	@Test
	void japanese() {
		assertEquals("ringo", JapaneseRomanizer.romanize("りんご"));
		assertEquals("ringo", JapaneseRomanizer.romanize("リンゴ"));
		assertEquals("daiyamondo no tsuruhashi", JapaneseRomanizer.romanize("だいやもんど の つるはし"));
		assertEquals("taimatsu", JapaneseRomanizer.romanize("たいまつ"));
		assertEquals("kyuuri", JapaneseRomanizer.romanize("きゅうり"));
		assertEquals("shakai chaya jagaimo", JapaneseRomanizer.romanize("しゃかい ちゃや じゃがいも"));
		assertEquals("kitto matcha", JapaneseRomanizer.romanize("きっと まっちゃ"));
		assertEquals("rubii", JapaneseRomanizer.romanize("ルビー"));
		assertEquals("tii faia wisukii", JapaneseRomanizer.romanize("ティー ファイア ウィスキー"));
		assertEquals("kin'youbi ninjin", JapaneseRomanizer.romanize("きんようび にんじん"));
		assertEquals("TNT", JapaneseRomanizer.romanize("TNT"));
	}
}
