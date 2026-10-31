package io.github.dylantopic.langmod.core.lang;

import java.util.Map;

/**
 * Russian in Latin letters, the way English-language maps and passports spell it (BGN/PCGN,
 * simplified: ы and й are y, the hard and soft signs are left out): Яблоко = Yabloko,
 * Железный слиток = Zheleznyy slitok.
 */
public final class RussianRomanizer {
	private static final Map<Character, String> LETTERS = Map.ofEntries(
			Map.entry('а', "a"), Map.entry('б', "b"), Map.entry('в', "v"), Map.entry('г', "g"), Map.entry('д', "d"),
			Map.entry('ж', "zh"), Map.entry('з', "z"), Map.entry('и', "i"), Map.entry('й', "y"), Map.entry('к', "k"),
			Map.entry('л', "l"), Map.entry('м', "m"), Map.entry('н', "n"), Map.entry('о', "o"), Map.entry('п', "p"),
			Map.entry('р', "r"), Map.entry('с', "s"), Map.entry('т', "t"), Map.entry('у', "u"), Map.entry('ф', "f"),
			Map.entry('х', "kh"), Map.entry('ц', "ts"), Map.entry('ч', "ch"), Map.entry('ш', "sh"), Map.entry('щ', "shch"),
			Map.entry('ъ', ""), Map.entry('ы', "y"), Map.entry('ь', ""), Map.entry('э', "e"), Map.entry('ю', "yu"),
			Map.entry('я', "ya"));
	private static final String VOWELS = "аеёиоуыэюя";

	private RussianRomanizer() {
	}

	public static String romanize(String text) {
		StringBuilder out = new StringBuilder();
		char previous = ' ';

		for (char ch : text.toCharArray()) {
			char lower = Character.toLowerCase(ch);
			String latin;

			if (lower == 'е' || lower == 'ё') {
				// ye/yo at the start of a word and after a vowel or a hard or soft sign, e/yo after other consonants.
				boolean start = !Character.isLetter(previous) || VOWELS.indexOf(previous) >= 0 || previous == 'ъ' || previous == 'ь' || previous == 'й';
				latin = lower == 'ё' ? "yo" : start ? "ye" : "e";
			} else {
				latin = LETTERS.get(lower);
			}

			if (latin == null) {
				out.append(ch);
			} else if (Character.isUpperCase(ch) && !latin.isEmpty()) {
				out.append(Character.toUpperCase(latin.charAt(0))).append(latin, 1, latin.length());
			} else {
				out.append(latin);
			}

			previous = lower;
		}

		return out.toString();
	}
}
