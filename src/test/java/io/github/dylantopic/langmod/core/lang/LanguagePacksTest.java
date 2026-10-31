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
import java.util.Map;

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

	static LanguagePack pack(Language language) throws IOException {
		Map<String, String> kana;

		try (Reader reader = Files.newBufferedReader(NAMES.resolve("ja_jp_kana.json"), StandardCharsets.UTF_8)) {
			kana = NameDictionary.load(reader, null, warning -> { }).entries();
		}

		return LanguagePack.forNames(language, names(language, null, new ArrayList<>()), kana);
	}

	static CheckResult check(LanguagePack pack, String key, String said) {
		NameEntry name = pack.name(key).orElseThrow();
		return pack.check(name, pack.normalize(said), MatchMode.STRICT, false);
	}

	@Test
	void bundledNamesLoadCompletely() throws IOException {
		for (Language language : Language.values()) {
			if (language == Language.MANDARIN) {
				continue;
			}

			List<String> warnings = new ArrayList<>();
			NameDictionary names = names(language, null, warnings);
			assertEquals(List.of(), warnings);
			assertTrue(names.size() > 1500, language + ": " + names.size());
		}

		assertTrue(pack(Language.BOSNIAN).name("block.minecraft.cherry_door").isEmpty(), "names left in English are skipped");

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

	@Test
	void russianAnswers() throws IOException {
		LanguagePack pack = pack(Language.RUSSIAN);
		NameEntry apple = pack.name("item.minecraft.apple").orElseThrow();
		assertEquals("Яблоко", apple.text());
		assertEquals("Yabloko", apple.pronunciation());
		assertTrue(check(pack, "item.minecraft.apple", "Яблоко.").exact());
		assertTrue(check(pack, "item.minecraft.bread", "хлеп").pass(), "a final б sounds like п");
		assertTrue(check(pack, "block.minecraft.green_wool", "Зеленая шерсть").pass(), "ё written as е");
		assertFalse(check(pack, "item.minecraft.apple", "Груша").pass());
		assertFalse(check(pack, "item.minecraft.apple", "Яблоки").pass(), "plural");
		assertEquals("yabloki", check(pack, "item.minecraft.apple", "Яблоки").heardPronunciation(), "answers are lowercased");
		assertTrue(pack.isNoise(pack.normalize("Субтитры сделал DimaTorzok"), apple));
	}

	@Test
	void dutchAnswers() throws IOException {
		LanguagePack pack = pack(Language.DUTCH);
		assertTrue(check(pack, "item.minecraft.apple", "De appel.").pass(), "article in front");
		assertTrue(check(pack, "block.minecraft.glass", "het glas").pass());
		assertTrue(check(pack, "block.minecraft.pumpkin", "Pompoeen").pass(), "a doubled vowel");
		assertTrue(check(pack, "item.minecraft.iron_ingot", "ijzerstaaf").pass());
		assertFalse(check(pack, "item.minecraft.apple", "Peer").pass());
		assertFalse(check(pack, "item.minecraft.apple", "Appels").pass(), "plural");
	}

	@Test
	void latinAnswers() throws IOException {
		LanguagePack pack = pack(Language.LATIN);
		assertEquals("Malum", pack.name("item.minecraft.apple").orElseThrow().text());
		assertTrue(check(pack, "item.minecraft.apple", "Malum.").exact());
		assertTrue(check(pack, "block.minecraft.glass", "bitrum").pass(), "v said b");
		assertTrue(check(pack, "block.minecraft.pumpkin", "kukurbita").pass(), "c written k");
		assertTrue(check(pack, "item.minecraft.diamond_pickaxe", "dolabra adamantina").pass());
		assertFalse(check(pack, "item.minecraft.apple", "Pirum").pass());
		assertFalse(check(pack, "item.minecraft.apple", "Maluus").pass(), "malus is not malum");
		assertFalse(check(pack, "block.minecraft.green_wool", "La na rubra").pass());
	}

	@Test
	void portugueseAnswers() throws IOException {
		LanguagePack pack = pack(Language.PORTUGUESE);
		assertTrue(check(pack, "item.minecraft.apple", "A maçã!").pass(), "article in front");
		assertTrue(check(pack, "item.minecraft.iron_ingot", "Lingote de Ferrro").pass(), "a tripled letter");
		assertTrue(check(pack, "item.minecraft.diamond_pickaxe", "picareta de diamante").pass());
		assertFalse(check(pack, "item.minecraft.apple", "Massa").pass(), "maçã has a nasal vowel");
		assertFalse(check(pack, "item.minecraft.apple", "Banana").pass());
		assertFalse(check(pack, "block.minecraft.green_wool", "Lã Vermelha").pass());
	}

	@Test
	void bosnianAnswers() throws IOException {
		LanguagePack pack = pack(Language.BOSNIAN);
		assertTrue(check(pack, "item.minecraft.apple", "Jabuka").exact());
		assertTrue(check(pack, "item.minecraft.diamond_pickaxe", "Dijamanski kramp").pass(), "t dropped between n and s");
		assertTrue(check(pack, "item.minecraft.apple", "jabuka.").pass());
		assertFalse(check(pack, "item.minecraft.apple", "Kruška").pass());
		assertFalse(check(pack, "item.minecraft.apple", "Jabuke").pass());
		assertFalse(check(pack, "block.minecraft.green_wool", "Crvena vuna").pass());
	}

	@Test
	void tagalogAnswers() throws IOException {
		LanguagePack pack = pack(Language.FILIPINO);
		assertEquals("Mansanas", pack.name("item.minecraft.apple").orElseThrow().text());
		assertTrue(check(pack, "item.minecraft.apple", "ang mansanas").pass(), "article in front");
		assertTrue(check(pack, "item.minecraft.diamond_pickaxe", "Jamanteng Piko").pass(), "diya said ja");
		assertFalse(check(pack, "item.minecraft.apple", "Saging").pass());
		assertFalse(check(pack, "block.minecraft.dirt", "Luya").pass());
		assertFalse(check(pack, "block.minecraft.green_wool", "Pulang Lana").pass());
	}

	@Test
	void japaneseAnswers() throws IOException {
		LanguagePack pack = pack(Language.JAPANESE);
		NameEntry torch = pack.name("block.minecraft.torch").orElseThrow();
		assertEquals("松明", torch.text());
		assertEquals("たいまつ  taimatsu", torch.pronunciation());
		assertEquals("ringo", pack.name("item.minecraft.apple").orElseThrow().pronunciation());
		assertTrue(check(pack, "item.minecraft.apple", "リンゴ。").exact());
		assertTrue(check(pack, "item.minecraft.apple", "りんご").pass(), "hiragana for katakana");
		assertTrue(check(pack, "block.minecraft.torch", "たいまつ").pass(), "kana for kanji");
		assertTrue(check(pack, "block.minecraft.torch", "タイマツ").pass());
		assertTrue(check(pack, "item.minecraft.iron_ingot", "鉄インゴット").exact());
		assertTrue(check(pack, "item.minecraft.iron_ingot", "てついんごと").pass(), "a small tsu blurred");
		assertTrue(check(pack, "block.minecraft.pumpkin", "かぼちゃ").pass());
		assertFalse(check(pack, "item.minecraft.apple", "バナナ").pass());
		assertFalse(check(pack, "item.minecraft.apple", "リング").pass(), "ring is not ringo");
		assertFalse(check(pack, "block.minecraft.green_wool", "赤色の羊毛").pass());
		assertEquals("banana", check(pack, "item.minecraft.apple", "バナナ").heardPronunciation());
		assertTrue(pack.isNoise(pack.normalize("ご視聴ありがとうございました"), pack.name("item.minecraft.apple").orElseThrow()));
		assertTrue(pack.name("block.minecraft.tnt").isEmpty(), "TNT is just Latin letters");
	}

	@Test
	void vietnameseAnswers() throws IOException {
		LanguagePack pack = pack(Language.VIETNAMESE);
		assertEquals("Táo", pack.name("item.minecraft.apple").orElseThrow().text());
		assertTrue(check(pack, "item.minecraft.apple", "Táo.").exact());
		assertTrue(check(pack, "item.minecraft.apple", "Quả táo").pass(), "classifier in front");
		assertTrue(check(pack, "item.minecraft.bread", "bánh mỳ").pass(), "i written y");
		assertTrue(check(pack, "block.minecraft.glass", "thuỷ tinh").pass(), "tone mark on another vowel");
		assertTrue(check(pack, "block.minecraft.dirt", "Đấc").pass(), "final t and c sound alike");
		assertTrue(check(pack, "item.minecraft.iron_ingot", "Phôi sắp").pass(), "final t and p sound alike");
		assertFalse(check(pack, "item.minecraft.apple", "Tàu").pass(), "another vowel and tone");

		CheckResult wrongTone = check(pack, "item.minecraft.apple", "Tào");
		assertFalse(wrongTone.pass());
		assertEquals(List.of(Status.TONE), wrongTone.expectedStatus());
		NameEntry apple = pack.name("item.minecraft.apple").orElseThrow();
		assertTrue(pack.check(apple, pack.normalize("Tào"), MatchMode.TONELESS, false).pass(), "toneless mode");

		assertFalse(check(pack, "item.minecraft.apple", "Chuối").pass());
		assertFalse(check(pack, "block.minecraft.green_wool", "Len đỏ").pass());
		assertTrue(pack.isNoise(pack.normalize("Hãy subscribe cho kênh Ghiền Mì Gõ"), apple));
	}

	@Test
	void germanAnswers() throws IOException {
		LanguagePack pack = pack(Language.GERMAN);
		assertTrue(check(pack, "item.minecraft.apple", "Apfel!").exact());
		assertTrue(check(pack, "item.minecraft.apple", "Der Apfel").pass(), "article in front");
		assertTrue(check(pack, "item.minecraft.bread", "Brod").pass(), "a final d sounds like t");
		assertTrue(check(pack, "item.minecraft.diamond_pickaxe", "Diamantspitzhacke").pass());
		assertFalse(check(pack, "item.minecraft.apple", "Äpfel").pass(), "the plural, with an umlaut");
		assertFalse(check(pack, "item.minecraft.apple", "Birne").pass());
		assertFalse(check(pack, "block.minecraft.green_wool", "Rote Wolle").pass());
	}
}
