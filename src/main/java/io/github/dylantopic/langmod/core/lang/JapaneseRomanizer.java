package io.github.dylantopic.langmod.core.lang;

import java.util.HashMap;
import java.util.Map;

/**
 * Kana in Latin letters (Hepburn): りんご = ringo, ダイヤモンド = daiyamondo, しゃ = sha, っか = kka,
 * ティ = ti. A long vowel mark repeats the vowel (ルビー = rubii). Other characters are kept.
 */
public final class JapaneseRomanizer {
	private static final Map<Integer, String> KANA = new HashMap<>();

	static {
		String[][] rows = {
				{"あいうえお", "a", "i", "u", "e", "o"}, {"かきくけこ", "ka", "ki", "ku", "ke", "ko"},
				{"さしすせそ", "sa", "shi", "su", "se", "so"}, {"たちつてと", "ta", "chi", "tsu", "te", "to"},
				{"なにぬねの", "na", "ni", "nu", "ne", "no"}, {"はひふへほ", "ha", "hi", "fu", "he", "ho"},
				{"まみむめも", "ma", "mi", "mu", "me", "mo"}, {"やゆよ", "ya", "yu", "yo"},
				{"らりるれろ", "ra", "ri", "ru", "re", "ro"}, {"わゐゑを", "wa", "i", "e", "o"},
				{"がぎぐげご", "ga", "gi", "gu", "ge", "go"}, {"ざじずぜぞ", "za", "ji", "zu", "ze", "zo"},
				{"だぢづでど", "da", "ji", "zu", "de", "do"}, {"ばびぶべぼ", "ba", "bi", "bu", "be", "bo"},
				{"ぱぴぷぺぽ", "pa", "pi", "pu", "pe", "po"}, {"ぁぃぅぇぉ", "a", "i", "u", "e", "o"},
				{"ゃゅょ", "ya", "yu", "yo"}, {"んゔゎ", "n", "vu", "wa"}};

		for (String[] row : rows) {
			for (int i = 0; i < row[0].length(); i++) {
				KANA.put((int) row[0].charAt(i), row[i + 1]);
			}
		}
	}

	private JapaneseRomanizer() {
	}

	public static String romanize(String text) {
		StringBuilder out = new StringBuilder();
		String hiragana = toHiragana(text);
		boolean doubleNext = false;

		for (int i = 0; i < hiragana.length(); i++) {
			char ch = hiragana.charAt(i);
			char next = i + 1 < hiragana.length() ? hiragana.charAt(i + 1) : 0;

			if (ch == 'っ') {
				doubleNext = true;
				continue;
			}

			String latin;

			if (ch == 'ー') {
				latin = lastVowel(out);
			} else if (!KANA.containsKey((int) ch)) {
				latin = String.valueOf(ch);
			} else if ("ゃゅょ".indexOf(next) >= 0 && "ゃゅょ".indexOf(ch) < 0 && ch != 'ん') {
				// きゃ = kya, しゃ = sha, ちゃ = cha, じゃ = ja, でゅ = dyu.
				String base = KANA.get((int) ch);
				String small = KANA.get((int) next);
				latin = base.endsWith("hi") || base.equals("ji") || base.equals("chi") ? base.substring(0, base.length() - 1) + small.substring(1)
						: consonant(base) + small;
				i++;
			} else if ("ぁぃぅぇぉ".indexOf(next) >= 0 && "ぁぃぅぇぉ".indexOf(ch) < 0) {
				// ティ = ti, ファ = fa, シェ = she, ウィ = wi.
				String base = KANA.get((int) ch);
				latin = (ch == 'う' ? "w" : consonant(base)) + KANA.get((int) next);
				i++;
			} else {
				latin = KANA.get((int) ch);

				if (ch == 'ん' && next != 0 && ("あいうえおやゆよ".indexOf(next) >= 0)) {
					latin = "n'";
				}
			}

			if (doubleNext) {
				doubleNext = false;

				if (!latin.isEmpty() && Character.isLetter(latin.charAt(0)) && "aeiou".indexOf(latin.charAt(0)) < 0) {
					latin = (latin.startsWith("ch") ? "t" : latin.substring(0, 1)) + latin;
				}
			}

			out.append(latin);
		}

		return out.toString();
	}

	/** Katakana as hiragana; everything else unchanged. */
	public static String toHiragana(String text) {
		StringBuilder out = new StringBuilder(text.length());

		for (char ch : text.toCharArray()) {
			out.append(ch >= 'ァ' && ch <= 'ヶ' ? (char) (ch - 0x60) : ch);
		}

		return out.toString();
	}

	public static boolean isKana(int cp) {
		return (cp >= 'ぁ' && cp <= 'ゖ') || (cp >= 'ァ' && cp <= 'ヺ') || cp == 'ー';
	}

	private static String consonant(String romaji) {
		return romaji.length() > 1 ? romaji.substring(0, romaji.length() - 1) : romaji;
	}

	private static String lastVowel(StringBuilder out) {
		for (int i = out.length() - 1; i >= 0; i--) {
			if ("aeiou".indexOf(out.charAt(i)) >= 0) {
				return String.valueOf(out.charAt(i));
			}
		}

		return "";
	}
}
