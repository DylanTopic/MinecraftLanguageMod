package io.github.dylantopic.langmod.core.tts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.dylantopic.langmod.core.lang.Language;

class VoiceChooserTest {
	private static final Voice DAVID = voice("Microsoft David", "en-US");
	private static final Voice HUIHUI = voice("Microsoft Huihui", "zh-CN");
	private static final Voice KANGKANG = voice("Microsoft Kangkang", "zh-CN");
	private static final Voice HANHAN = voice("Microsoft Hanhan", "zh-TW");
	private static final Voice TRACY = voice("Microsoft Tracy", "zh-HK");
	private static final Voice HELENA = voice("Microsoft Helena", "es-ES");
	private static final Voice SABINA = voice("Microsoft Sabina", "es-MX");
	private static final Voice ELSA = voice("Microsoft Elsa", "it-IT");
	private static final Voice MATEJ = voice("Microsoft Matej", "hr-HR");
	private static final Voice HARUKA = voice("Microsoft Haruka", "ja-JP");

	private static Voice voice(String name, String locale) {
		return new Voice("id:" + name, name, locale, "");
	}

	@Test
	void prefersTheLanguagesOwnRegion() {
		assertEquals(SABINA, VoiceChooser.choose(Language.SPANISH, List.of(DAVID, HELENA, SABINA), "").orElseThrow());
		assertEquals(HELENA, VoiceChooser.choose(Language.SPANISH, List.of(DAVID, HELENA), "").orElseThrow(), "Spain's voice when Mexico has none");
		assertEquals(HARUKA, VoiceChooser.choose(Language.JAPANESE, List.of(DAVID, HUIHUI, HARUKA), "").orElseThrow());
		assertEquals(DAVID, VoiceChooser.choose(Language.ENGLISH, List.of(HUIHUI, DAVID), "").orElseThrow(), "English Windows' own voice");
	}

	@Test
	void mandarinNeverGetsCantonese() {
		assertEquals(HUIHUI, VoiceChooser.choose(Language.MANDARIN, List.of(TRACY, HANHAN, HUIHUI), "").orElseThrow());
		assertEquals(HANHAN, VoiceChooser.choose(Language.MANDARIN, List.of(TRACY, HANHAN), "").orElseThrow(), "Taiwan's Mandarin");
		assertTrue(VoiceChooser.choose(Language.MANDARIN, List.of(TRACY, DAVID), "").isEmpty(), "Hong Kong voices speak Cantonese");
	}

	@Test
	void borrowedVoices() {
		assertEquals(ELSA, VoiceChooser.choose(Language.LATIN, List.of(DAVID, ELSA), "").orElseThrow(), "church Latin, read by an Italian voice");
		assertEquals(MATEJ, VoiceChooser.choose(Language.BOSNIAN, List.of(DAVID, MATEJ), "").orElseThrow(), "Bosnian read by a Croatian voice");
		assertTrue(VoiceChooser.choose(Language.FILIPINO, List.of(DAVID, HELENA, ELSA), "").isEmpty(), "no voice reads Tagalog");
	}

	@Test
	void chosenVoiceWins() {
		List<Voice> voices = List.of(HUIHUI, KANGKANG);
		assertEquals(List.of(HUIHUI, KANGKANG), VoiceChooser.candidates(Language.MANDARIN, voices));
		assertEquals(KANGKANG, VoiceChooser.choose(Language.MANDARIN, voices, "kangkang").orElseThrow());
		assertEquals(HUIHUI, VoiceChooser.choose(Language.MANDARIN, voices, "Yaoyao").orElseThrow(), "a voice no longer installed is ignored");
	}

	@Test
	void localeMatching() {
		assertTrue(VoiceChooser.matches("es-MX", "es"));
		assertTrue(VoiceChooser.matches("sr-Latn-RS", "sr-Latn"));
		assertTrue(VoiceChooser.matches("zh_CN", "zh-CN"));
		assertFalse(VoiceChooser.matches("zh-HK", "zh-CN"));
		assertFalse(VoiceChooser.matches("et-EE", "e"));
	}
}
