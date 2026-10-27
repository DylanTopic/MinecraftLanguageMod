package io.github.dylantopic.langmod.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

class PinyinTest {
	@Test
	void parsesToneNumbers() {
		assertEquals(new Pinyin.Syllable("ping", 2), Pinyin.parse("ping2"));
		assertEquals(new Pinyin.Syllable("lv", 4), Pinyin.parse("lv4"));
		assertEquals(new Pinyin.Syllable("lv", 4), Pinyin.parse("lü4"));
		assertEquals(new Pinyin.Syllable("lv", 4), Pinyin.parse("lu:4"));
		assertEquals(new Pinyin.Syllable("de", 5), Pinyin.parse("de5"));
		assertEquals(new Pinyin.Syllable("de", 5), Pinyin.parse("de0"));
		assertEquals(new Pinyin.Syllable("de", 5), Pinyin.parse("de"));
		assertEquals(new Pinyin.Syllable("zhong", 1), Pinyin.parse(" Zhong1 "));
	}

	@Test
	void parsesToneMarks() {
		assertEquals(new Pinyin.Syllable("guo", 3), Pinyin.parse("guǒ"));
		assertEquals(new Pinyin.Syllable("lv", 4), Pinyin.parse("lǜ"));
		assertEquals(new Pinyin.Syllable("nv", 3), Pinyin.parse("nǚ"));
		assertEquals(new Pinyin.Syllable("lv", 5), Pinyin.parse("lü"));
		assertEquals(List.of(new Pinyin.Syllable("ping", 2), new Pinyin.Syllable("guo", 3)), Pinyin.parseAll("píng  guǒ"));
	}

	@Test
	void placesToneMarks() {
		assertEquals("píng", Pinyin.parse("ping2").marked());
		assertEquals("guǒ", Pinyin.parse("guo3").marked());
		assertEquals("lǜ", Pinyin.parse("lv4").marked());
		assertEquals("huā", Pinyin.parse("hua1").marked());
		assertEquals("xiè", Pinyin.parse("xie4").marked());
		assertEquals("lóu", Pinyin.parse("lou2").marked());
		assertEquals("guī", Pinyin.parse("gui1").marked());
		assertEquals("niú", Pinyin.parse("niu2").marked());
		assertEquals("xué", Pinyin.parse("xue2").marked());
		assertEquals("lüè", Pinyin.parse("lve4").marked());
		assertEquals("de", Pinyin.parse("de5").marked());
		assertEquals("ér", Pinyin.parse("er2").marked());
		assertEquals("fù mó jīn píng guǒ", Pinyin.marked(Pinyin.parseAll("fu4 mo2 jin1 ping2 guo3")));
	}

	@Test
	void rejectsGarbage() {
		assertThrows(IllegalArgumentException.class, () -> Pinyin.parse(""));
		assertThrows(IllegalArgumentException.class, () -> Pinyin.parse("pi ng"));
		assertThrows(IllegalArgumentException.class, () -> Pinyin.parse("苹"));
	}
}
