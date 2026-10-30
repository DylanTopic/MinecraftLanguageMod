package io.github.dylantopic.langmod.core.lang;

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

import org.junit.jupiter.api.Test;

import io.github.dylantopic.langmod.core.MatchMode;
import io.github.dylantopic.langmod.core.Matcher.Status;

/** The Spanish, French and Korean name files and how answers are judged against them. */
class LanguagePacksTest {
	static final Path NAMES = Path.of("src/main/resources/assets/langmod/names");

	static NameDictionary names(Language language, Reader overrides, List<String> warnings) throws IOException {
		try (Reader reader = Files.newBufferedReader(NAMES.resolve(language.id() + ".json"), StandardCharsets.UTF_8)) {
			return NameDictionary.load(reader, overrides, warnings::add);
		}
	}

	static WordPack spanish() throws IOException {
		return new WordPack(Language.SPANISH, WordRules.SPANISH, names(Language.SPANISH, null, new ArrayList<>()));
	}

	static WordPack french() throws IOException {
		return new WordPack(Language.FRENCH, WordRules.FRENCH, names(Language.FRENCH, null, new ArrayList<>()));
	}

	static KoreanPack korean() throws IOException {
		return new KoreanPack(names(Language.KOREAN, null, new ArrayList<>()));
	}

	static CheckResult check(LanguagePack pack, String key, String said) {
		NameEntry name = pack.name(key).orElseThrow();
		return pack.check(name, pack.normalize(said), MatchMode.STRICT, false);
	}

	@Test
	void bundledNamesLoadCompletely() throws IOException {
		for (Language language : List.of(Language.SPANISH, Language.FRENCH, Language.KOREAN)) {
			List<String> warnings = new ArrayList<>();
			NameDictionary names = names(language, null, warnings);
			assertEquals(List.of(), warnings);
			assertTrue(names.size() > 1500, language + ": " + names.size());
		}

		assertEquals("Manzana", spanish().name("item.minecraft.apple").orElseThrow().text());
		assertEquals("Botella", spanish().name("item.minecraft.glass_bottle").orElseThrow().text()); // Mexican, not "Frasco de cristal"
		assertEquals("Pomme", french().name("item.minecraft.apple").orElseThrow().text());
		NameEntry apple = korean().name("item.minecraft.apple").orElseThrow();
		assertEquals("사과", apple.text());
		assertEquals("sagwa", apple.pronunciation());
		assertTrue(korean().name("block.minecraft.tnt").isEmpty()); // "TNT", picked up normally as in Mandarin
		assertTrue(korean().name("item.minecraft.tnt_minecart").isEmpty());
		assertEquals("TNT", spanish().name("block.minecraft.tnt").orElseThrow().text());
	}

	@Test
	void overridesWin() throws IOException {
		List<String> warnings = new ArrayList<>();
		NameDictionary names = names(Language.SPANISH,
				new StringReader("{\"_comment\": \"x\", \"item.minecraft.apple\": \"Manzana roja\", \"item.minecraft.stick\": 5}"), warnings);
		assertEquals("Manzana roja", names.get("item.minecraft.apple").orElseThrow());
		assertEquals(1, names.overrideCount());
		assertEquals(1, warnings.size());
	}

	@Test
	void spanishAnswers() throws IOException {
		WordPack pack = spanish();
		assertTrue(check(pack, "item.minecraft.apple", "Manzana.").exact());
		assertTrue(check(pack, "item.minecraft.apple", "¡La manzana!").pass(), "article in front");
		assertTrue(check(pack, "item.minecraft.apple", "una manzana").pass());
		assertTrue(check(pack, "item.minecraft.apple", "mansana").pass(), "s and z sound the same");
		assertTrue(check(pack, "block.minecraft.glass", "Bithrio").pass(), "the soft d written as th");
		assertFalse(check(pack, "item.minecraft.apple", "manzanas").pass(), "plural is a different word");
		assertFalse(check(pack, "item.minecraft.apple", "plátano").pass());
		assertTrue(check(pack, "item.minecraft.diamond_pickaxe", "pico de diamante").pass());
		assertTrue(check(pack, "item.minecraft.diamond_pickaxe", "pico de diamánte").pass(), "accents are ignored");

		CheckResult missing = check(pack, "item.minecraft.diamond_pickaxe", "pico diamante");
		assertFalse(missing.pass());
		assertEquals(List.of(Status.MATCH, Status.MISSING, Status.MATCH), missing.expectedStatus());
		assertEquals(1, missing.errors());

		CheckResult extra = check(pack, "item.minecraft.apple", "la manzana roja");
		assertFalse(extra.pass());
		assertEquals(List.of(Status.MATCH, Status.MATCH, Status.EXTRA), extra.heardTokens().stream().map(CheckResult.Token::status).toList());
	}

	@Test
	void frenchAnswers() throws IOException {
		WordPack pack = french();
		assertTrue(check(pack, "item.minecraft.apple", "Une pomme.").pass());
		assertTrue(check(pack, "item.minecraft.bow", "L'arc").pass(), "elided article");
		assertTrue(check(pack, "block.minecraft.glass", "Vert.").pass(), "verre and vert sound the same");
		assertTrue(check(pack, "item.minecraft.golden_apple", "pomme doré").pass(), "dorée and doré sound the same");
		assertFalse(check(pack, "block.minecraft.green_wool", "laine vert").pass(), "verte and vert do not");
		assertTrue(check(pack, "block.minecraft.green_wool", "de la laine verte").pass());
		assertTrue(check(pack, "block.minecraft.gold_block", "bloc d'or").pass());
		assertTrue(check(pack, "block.minecraft.gold_block", "bloc dor").pass(), "apostrophes do not matter");
		assertFalse(check(pack, "item.minecraft.apple", "poire").pass());
		assertFalse(check(pack, "item.minecraft.apple", "pomp").pass(), "a nasal vowel is not pomme");
		assertFalse(check(pack, "item.minecraft.apple", "pompe").pass());
		assertTrue(check(pack, "item.minecraft.apple", "pommes").pass(), "silent plural s");
		assertTrue(check(pack, "item.minecraft.apple", "POM").pass(), "a final m is not nasal");
		assertTrue(check(pack, "block.minecraft.mycelium", "mycéliums").pass(), "nor before a plural s");
		assertFalse(check(pack, "item.minecraft.bread", "pan").pass(), "pain and pan have different nasal vowels");
		assertTrue(check(pack, "item.minecraft.diamond_pickaxe", "pioche en diamand").pass(), "silent final consonant after a nasal vowel");
		assertTrue(pack.isNoise(pack.normalize("Sous-titres réalisés para la communauté d'Amara.org"), pack.name("item.minecraft.apple").orElseThrow()));
	}

	@Test
	void koreanAnswers() throws IOException {
		KoreanPack pack = korean();
		assertTrue(check(pack, "item.minecraft.apple", "사과.").exact());
		assertTrue(check(pack, "block.minecraft.green_wool", "초록색양털").pass(), "spaces do not matter");
		assertTrue(check(pack, "item.minecraft.diamond_pickaxe", "다이아몬드 곡괭이!").pass());

		CheckResult wrong = check(pack, "item.minecraft.apple", "사가");
		assertFalse(wrong.pass());
		assertEquals(List.of(Status.MATCH, Status.WRONG), wrong.expectedStatus());
		assertEquals("saga", wrong.heardPronunciation());

		assertTrue(pack.check(new NameEntry("k", "개", "gae"), pack.normalize("게"), MatchMode.STRICT, false).pass(), "ㅐ and ㅔ sound alike");
		assertTrue(pack.isNoise(pack.normalize("시청해주셔서 감사합니다."), pack.name("item.minecraft.apple").orElseThrow()));
		assertFalse(pack.isNoise("사과", pack.name("item.minecraft.apple").orElseThrow()));
	}
}
