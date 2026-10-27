package io.github.dylantopic.langmod.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

class ItemDictionaryTest {
	static final Path ITEMS = Path.of("src/main/resources/assets/langmod/pinyin/items.json");

	@Test
	void bundledDictionaryLoadsCompletely() throws IOException {
		List<String> warnings = new ArrayList<>();
		ItemDictionary dictionary;

		try (Reader reader = Files.newBufferedReader(ITEMS, StandardCharsets.UTF_8)) {
			dictionary = ItemDictionary.load(reader, null, warnings::add);
		}

		assertEquals(List.of(), warnings);
		assertTrue(dictionary.size() > 1500, "expected every vanilla item, got " + dictionary.size());
		PinyinEntry apple = dictionary.get("item.minecraft.apple").orElseThrow();
		assertEquals("苹果", apple.hanzi());
		assertEquals("píng guǒ", apple.marks());
		assertEquals("ping2 guo3", apple.numbers());
		assertEquals("lǜ sè yáng máo", dictionary.get("block.minecraft.green_wool").orElseThrow().marks());
	}

	/** The Python generator writes tone marks with pypinyin; the mod renders them itself. They must agree. */
	@Test
	void javaToneMarksMatchGenerator() throws IOException {
		JsonObject json;

		try (Reader reader = Files.newBufferedReader(ITEMS, StandardCharsets.UTF_8)) {
			json = JsonParser.parseReader(reader).getAsJsonObject();
		}

		List<String> mismatches = new ArrayList<>();

		for (Map.Entry<String, JsonElement> e : json.entrySet()) {
			JsonObject entry = e.getValue().getAsJsonObject();
			String expected = entry.get("pinyin_marks").getAsString();
			String actual = Pinyin.marked(Pinyin.parseAll(entry.get("pinyin_numbers").getAsString()));

			if (!expected.equals(actual)) {
				mismatches.add(e.getKey() + ": " + expected + " vs " + actual);
			}
		}

		assertEquals(List.of(), mismatches);
	}

	@Test
	void overridesWinAndCanFixOnlyThePinyin() {
		String bundled = """
				{"item.minecraft.apple": {"hanzi": "苹果", "pinyin_marks": "píng guǒ", "pinyin_numbers": "ping2 guo3"},
				 "block.minecraft.dirt": {"hanzi": "泥土", "pinyin_marks": "ní tǔ", "pinyin_numbers": "ni2 tu3"}}
				""";
		String overrides = """
				{"_comment": "ignored",
				 "item.minecraft.apple": {"pinyin_numbers": "ping2 guo5"},
				 "item.minecraft.stick": {"hanzi": "木棍", "pinyin_marks": "mù gùn"},
				 "item.minecraft.broken": {"hanzi": "坏", "pinyin_numbers": "huai4 le5"}}
				""";
		List<String> warnings = new ArrayList<>();
		ItemDictionary dictionary = ItemDictionary.load(new StringReader(bundled), new StringReader(overrides), warnings::add);

		assertEquals("píng guo", dictionary.get("item.minecraft.apple").orElseThrow().marks());
		assertEquals("mu4 gun4", dictionary.get("item.minecraft.stick").orElseThrow().numbers());
		assertEquals("ní tǔ", dictionary.get("block.minecraft.dirt").orElseThrow().marks());
		assertFalse(dictionary.get("item.minecraft.broken").isPresent());
		assertEquals(1, warnings.size());
		assertEquals(2, dictionary.overrideCount());
	}
}
