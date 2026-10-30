package io.github.dylantopic.langmod.core.lang;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class LanguageTest {
	@Test
	void unitsAndLengths() {
		assertEquals(List.of("苹", "果"), Language.MANDARIN.units("苹果"));
		assertEquals(2, Language.MANDARIN.length("苹果"));
		assertEquals(List.of("Pico", "de", "diamante"), Language.SPANISH.units("Pico de diamante"));
		assertEquals(3, Language.SPANISH.length("Pico de diamante"));
		assertEquals(List.of("Bloc", "d'or"), Language.FRENCH.units("Bloc d'or"));
		assertEquals(List.of("arc", "en", "ciel"), Language.FRENCH.units("arc-en-ciel"));
		assertEquals(1, Language.FRENCH.length("arc-en-ciel"));
		assertEquals(List.of("초", "록", "색", "양", "털"), Language.KOREAN.units("초록색 양털"));
		assertEquals(2, Language.KOREAN.length("초록색 양털"));
		assertEquals(List.of("TNT"), Language.KOREAN.units("TNT"));
	}

	@Test
	void lookup() {
		assertEquals(Optional.of(Language.SPANISH), Language.byId("es_mx"));
		assertEquals(Optional.empty(), Language.byId("es_es"));
		assertEquals("ko", Language.KOREAN.whisperCode());
	}
}
