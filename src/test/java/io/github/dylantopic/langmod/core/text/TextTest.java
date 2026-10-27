package io.github.dylantopic.langmod.core.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.github.dylantopic.langmod.core.Pinyin;

class TextTest {
	static final Path ASSETS = Path.of("src/main/resources/assets/langmod");
	static ChineseConverter converter;
	static CharReadings readings;

	@BeforeAll
	static void load() throws IOException {
		try (Reader phrases = Files.newBufferedReader(ASSETS.resolve("opencc/TSPhrases.txt"), StandardCharsets.UTF_8);
				Reader characters = Files.newBufferedReader(ASSETS.resolve("opencc/TSCharacters.txt"), StandardCharsets.UTF_8);
				Reader chars = Files.newBufferedReader(ASSETS.resolve("pinyin/chars.tsv"), StandardCharsets.UTF_8)) {
			converter = ChineseConverter.load(phrases, characters);
			readings = CharReadings.load(chars);
		}
	}

	@Test
	void convertsTraditionalToSimplified() {
		assertEquals("苹果", converter.toSimplified("蘋果"));
		assertEquals("钻石剑", converter.toSimplified("鑽石劍"));
		assertEquals("绿色羊毛", converter.toSimplified("綠色羊毛"));
		assertEquals("铁头盔", converter.toSimplified("鐵頭盔"));
		assertEquals("附魔金苹果", converter.toSimplified("附魔金蘋果"));
		// Already simplified text is left alone.
		assertEquals("苹果", converter.toSimplified("苹果"));
		// Phrase dictionary: 乾 is 干 (dry) in 乾燥 but stays 乾 in 乾坤.
		assertEquals("干燥", converter.toSimplified("乾燥"));
		assertEquals("乾坤", converter.toSimplified("乾坤"));
	}

	@Test
	void normalizesTranscriptions() {
		TextNormalizer normalizer = new TextNormalizer(converter);
		assertEquals("苹果", normalizer.normalize(" 蘋果。"));
		assertEquals("苹果", normalizer.normalize("苹果!"));
		assertEquals("苹果", normalizer.normalize("「蘋果」？"));
		assertEquals("三叉戟", normalizer.normalize("3叉戟"));
		assertEquals("三叉戟", normalizer.normalize("３叉戟"));
		assertEquals("tnt矿车", normalizer.normalize("TNT 礦車"));
		assertEquals("", normalizer.normalize("…… ，"));
	}

	@Test
	void knowsEveryReading() {
		assertTrue(readings.size() > 20000);
		assertEquals(Pinyin.parse("ping2"), readings.of("苹".codePointAt(0)).getFirst(), "most common reading first");
		List<Pinyin.Syllable> chang = readings.of("长".codePointAt(0));
		assertTrue(chang.contains(Pinyin.parse("chang2")) && chang.contains(Pinyin.parse("zhang3")), chang.toString());
		assertEquals(List.of(), readings.of('a'));
	}
}
