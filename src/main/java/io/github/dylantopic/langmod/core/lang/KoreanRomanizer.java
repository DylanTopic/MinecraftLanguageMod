package io.github.dylantopic.langmod.core.lang;

import java.util.ArrayList;
import java.util.List;

/**
 * Korean to Latin letters following the Revised Romanization of Korean, including the sound
 * changes it writes down inside a word: a final consonant carried over to a following vowel
 * (마법이 mabeobi), nasalization (국물 gungmul), ㄹ assimilation (신라 silla, 종로 jongno),
 * aspiration with ㅎ (좋고 joko) and palatalization (같이 gachi). Words are romanized
 * separately; anything that is not Hangul is kept as it is.
 */
public final class KoreanRomanizer {
	private static final String[] INITIALS = {"g", "kk", "n", "d", "tt", "r", "m", "b", "pp", "s", "ss", "", "j", "jj", "ch", "k", "t", "p", "h"};
	private static final String[] VOWELS = {"a", "ae", "ya", "yae", "eo", "e", "yeo", "ye", "o", "wa", "wae", "oe", "yo", "u", "wo", "we", "wi", "yu", "eu", "ui", "i"};
	private static final String[] FINALS = {"", "k", "k", "k", "n", "n", "n", "t", "l", "k", "m", "l", "l", "l", "p", "l", "m", "p", "p", "t", "t", "ng", "t", "t", "k", "t", "p", "t"};

	// Initial consonant indexes.
	private static final int G = 0, N = 2, D = 3, R = 5, M = 6, B = 7, S = 9, SILENT = 11, J = 12, CH = 14, K = 15, T = 16, P = 17, H = 18;
	// Final consonant indexes.
	private static final int F_N = 4, F_NH = 6, F_L = 8, F_M = 16, F_NG = 21, F_H = 27, F_LH = 15;

	/** Final consonant index -> {final left behind, initial moved to a following vowel} when a vowel follows. */
	private static final int[][] CARRY = {
			{0, -1}, {0, G}, {0, 1}, {1, S}, {0, N}, {F_N, J}, {0, N}, {0, D}, {0, R}, {F_L, G}, {F_L, M}, {F_L, B}, {F_L, S}, {F_L, T},
			{F_L, P}, {0, R}, {0, M}, {0, B}, {17, S}, {0, S}, {0, 10}, {F_NG, -1}, {0, J}, {0, CH}, {0, K}, {0, T}, {0, P}, {0, SILENT}
	};

	private KoreanRomanizer() {
	}

	public static String romanize(String text) {
		List<String> words = new ArrayList<>();

		for (String word : text.trim().split("\\s+")) {
			if (!word.isEmpty()) {
				words.add(romanizeWord(word));
			}
		}

		return String.join(" ", words);
	}

	private static String romanizeWord(String word) {
		int[] cps = word.codePoints().toArray();
		int[] initial = new int[cps.length];
		int[] last = new int[cps.length];

		for (int i = 0; i < cps.length; i++) {
			if (Hangul.isSyllable(cps[i])) {
				initial[i] = Hangul.initial(cps[i]);
				last[i] = Hangul.last(cps[i]);
			}
		}

		boolean[] lateral = new boolean[cps.length]; // an initial ㄹ that follows a final ㄹ is written "l"

		for (int i = 0; i + 1 < cps.length; i++) {
			if (Hangul.isSyllable(cps[i]) && Hangul.isSyllable(cps[i + 1])) {
				applySoundChange(i, cps, initial, last, lateral);
			}
		}

		StringBuilder out = new StringBuilder();

		for (int i = 0; i < cps.length; i++) {
			if (!Hangul.isSyllable(cps[i])) {
				out.appendCodePoint(cps[i]);
				continue;
			}

			out.append(lateral[i] ? "l" : INITIALS[initial[i]]).append(VOWELS[Hangul.vowel(cps[i])]).append(FINALS[last[i]]);
		}

		return out.toString();
	}

	/** Adjusts the final of syllable i and the initial of syllable i + 1 for how they are pronounced together. */
	private static void applySoundChange(int i, int[] cps, int[] initial, int[] last, boolean[] lateral) {
		int fin = last[i];
		int next = initial[i + 1];

		if (fin == 0) {
			return;
		}

		if (next == SILENT) {
			int[] carry = CARRY[fin];

			if (carry[1] >= 0) {
				last[i] = carry[0];
				int moved = carry[1];

				if (Hangul.vowel(cps[i + 1]) == 20 && (moved == D || moved == T)) {
					moved = moved == D ? J : CH; // 굳이 guji, 같이 gachi
				}

				initial[i + 1] = moved;
			}

			return;
		}

		if (next == H && (isK(fin) || isT(fin) || isP(fin))) {
			last[i] = fin == 9 ? F_L : fin == 11 ? F_L : 0;
			initial[i + 1] = isK(fin) ? K : isT(fin) ? (fin == 22 || fin == 23 ? CH : T) : P;
			return;
		}

		if ((fin == F_H || fin == F_NH || fin == F_LH) && (next == G || next == D || next == J || next == N)) {
			last[i] = fin == F_NH ? F_N : fin == F_LH ? F_L : next == N ? F_N : 0;

			if (next != N) {
				initial[i + 1] = next == G ? K : next == D ? T : CH;
			}

			return;
		}

		if (next == R) {
			if (fin == F_N || fin == F_L) {
				last[i] = F_L;
				lateral[i + 1] = true; // 신라 silla
			} else if (fin == F_M || fin == F_NG) {
				initial[i + 1] = N; // 종로 jongno
			} else if (isK(fin)) {
				last[i] = F_NG;
				initial[i + 1] = N; // 독립 dongnip
			} else if (isP(fin)) {
				last[i] = F_M;
				initial[i + 1] = N; // 십리 simni
			}

			return;
		}

		if (fin == F_L && next == N) {
			lateral[i + 1] = true; // 설날 seollal
			return;
		}

		if (next == N || next == M) {
			if (isK(fin)) {
				last[i] = F_NG; // 국물 gungmul
			} else if (isT(fin)) {
				last[i] = F_N;
			} else if (isP(fin)) {
				last[i] = F_M; // 합니다 hamnida
			}
		}
	}

	private static boolean isK(int fin) {
		return fin == 1 || fin == 2 || fin == 3 || fin == 9 || fin == 24;
	}

	private static boolean isT(int fin) {
		return fin == 7 || fin == 19 || fin == 20 || fin == 22 || fin == 23 || fin == 25;
	}

	private static boolean isP(int fin) {
		return fin == 11 || fin == 14 || fin == 17 || fin == 18 || fin == 26;
	}
}
