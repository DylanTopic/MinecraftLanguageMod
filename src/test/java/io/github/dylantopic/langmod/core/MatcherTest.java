package io.github.dylantopic.langmod.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.github.dylantopic.langmod.core.Matcher.Status;
import io.github.dylantopic.langmod.core.text.CharReadings;

class MatcherTest {
	static ItemDictionary items;
	static CharReadings readings;

	@BeforeAll
	static void load() throws IOException {
		Path assets = Path.of("src/main/resources/assets/langmod/pinyin");

		try (Reader json = Files.newBufferedReader(assets.resolve("items.json"), StandardCharsets.UTF_8);
				Reader chars = Files.newBufferedReader(assets.resolve("chars.tsv"), StandardCharsets.UTF_8)) {
			items = ItemDictionary.load(json, null, message -> { });
			readings = CharReadings.load(chars);
		}
	}

	static Matcher.Result match(String key, String heard, MatchMode mode) {
		return Matcher.match(items.get(key).orElseThrow(), heard, readings, mode, false);
	}

	@Test
	void exactCharactersPass() {
		Matcher.Result result = match("item.minecraft.apple", "苹果", MatchMode.STRICT);
		assertTrue(result.pass() && result.exactCharacters());
		assertEquals("píng guǒ", result.heardPinyin());
		assertTrue(match("item.minecraft.apple", "苹果苹果", MatchMode.STRICT).pass(), "Whisper repeating the word");
	}

	@Test
	void homophonesPassOnPinyin() {
		// 平 is píng like 苹: same sound, other character.
		Matcher.Result result = match("item.minecraft.apple", "平果", MatchMode.STRICT);
		assertTrue(result.pass());
		assertFalse(result.exactCharacters());
		assertEquals(List.of(Status.MATCH, Status.MATCH), result.expectedStatus());
		// 闪长岩: 常 is cháng, the reading of 长 in this word.
		assertTrue(match("block.minecraft.diorite", "闪常岩", MatchMode.STRICT).pass());
	}

	@Test
	void wrongToneFailsOnlyWhenTonesCount() {
		// 平国 = píng guó, expected píng guǒ.
		Matcher.Result strict = match("item.minecraft.apple", "平国", MatchMode.STRICT);
		assertFalse(strict.pass());
		assertEquals(List.of(Status.MATCH, Status.TONE), strict.expectedStatus());
		assertEquals("píng guó", strict.heardPinyin());
		assertTrue(match("item.minecraft.apple", "平国", MatchMode.TONELESS).pass());
		// 你吐 = nǐ tǔ, expected 泥土 ní tǔ.
		assertEquals(List.of(Status.TONE, Status.MATCH), match("block.minecraft.dirt", "你吐", MatchMode.STRICT).expectedStatus());
		assertTrue(match("block.minecraft.dirt", "你吐", MatchMode.TONELESS).pass());
		// Lenient only relaxes names of four or more syllables.
		assertFalse(match("block.minecraft.dirt", "你吐", MatchMode.LENIENT).pass());
	}

	@Test
	void wrongWordsFail() {
		Matcher.Result banana = match("item.minecraft.apple", "香蕉", MatchMode.TONELESS);
		assertFalse(banana.pass());
		assertEquals(List.of(Status.WRONG, Status.WRONG), banana.expectedStatus());
		// 西瓜 vs 南瓜: only the first syllable is wrong.
		Matcher.Result melon = match("block.minecraft.pumpkin", "西瓜", MatchMode.STRICT);
		assertEquals(List.of(Status.WRONG, Status.MATCH), melon.expectedStatus());
		assertEquals(1, melon.errors());
		assertFalse(match("item.minecraft.apple", "apple", MatchMode.TONELESS).pass());
	}

	@Test
	void missingAndExtraSyllablesArePinpointed() {
		Matcher.Result missing = match("item.minecraft.diamond_sword", "钻石", MatchMode.STRICT);
		assertEquals(List.of(Status.MATCH, Status.MATCH, Status.MISSING), missing.expectedStatus());
		Matcher.Result extra = match("item.minecraft.diamond_sword", "钻石剑啊", MatchMode.STRICT);
		assertFalse(extra.pass());
		assertEquals(Status.EXTRA, extra.heardTokens().get(3).status());
		assertEquals(1, extra.errors());
	}

	@Test
	void longNamesAllowOneSlip() {
		PinyinEntry goldenApple = items.get("item.minecraft.enchanted_golden_apple").orElseThrow();
		String oneSlip = "附魔金平国"; // guó instead of guǒ
		assertFalse(Matcher.match(goldenApple, oneSlip, readings, MatchMode.STRICT, false).pass());
		assertTrue(Matcher.match(goldenApple, oneSlip, readings, MatchMode.STRICT, true).pass(), "over the length limit");
		assertTrue(Matcher.match(goldenApple, oneSlip, readings, MatchMode.LENIENT, false).pass(), "lenient mode, 5 syllables");
		assertFalse(Matcher.match(goldenApple, "附魔平国", readings, MatchMode.LENIENT, false).pass(), "two errors");
	}

	@Test
	void expectedNeutralToneAcceptsAnyTone() {
		PinyinEntry entry = PinyinEntry.of("test", "石头", Pinyin.parseAll("shi2 tou5"));
		assertTrue(Matcher.match(entry, "石投", readings, MatchMode.STRICT, false).pass(), "投 is tóu");
	}

	@Test
	void nothingHeardFails() {
		Matcher.Result result = match("item.minecraft.apple", "", MatchMode.TONELESS);
		assertFalse(result.pass());
		assertEquals(List.of(Status.MISSING, Status.MISSING), result.expectedStatus());
	}
}
