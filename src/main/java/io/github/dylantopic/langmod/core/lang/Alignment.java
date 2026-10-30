package io.github.dylantopic.langmod.core.lang;

import java.util.function.BiFunction;

import io.github.dylantopic.langmod.core.Matcher.Status;

/**
 * Edit-distance alignment of the units of an expected name with the units heard, labelling every
 * unit on both sides, so a wrong, missing or extra word is pinpointed instead of shifting
 * everything after it. (The Mandarin {@link io.github.dylantopic.langmod.core.Matcher} does the
 * same with pinyin readings.)
 */
final class Alignment {
	/**
	 * @param expected status of each expected unit (MATCH, WRONG or MISSING)
	 * @param heard status of each heard unit (MATCH, WRONG or EXTRA)
	 */
	record Outcome(Status[] expected, Status[] heard, int errors) {
	}

	private Alignment() {
	}

	/** @param compare status of expected unit i against heard unit j: MATCH or WRONG */
	static Outcome align(int n, int m, BiFunction<Integer, Integer, Status> compare) {
		Status[][] pair = new Status[n][m];
		int[][] cost = new int[n + 1][m + 1];

		for (int i = 0; i <= n; i++) {
			cost[i][0] = i;
		}

		for (int j = 0; j <= m; j++) {
			cost[0][j] = j;
		}

		for (int i = 1; i <= n; i++) {
			for (int j = 1; j <= m; j++) {
				pair[i - 1][j - 1] = compare.apply(i - 1, j - 1);
				int substitute = cost[i - 1][j - 1] + (pair[i - 1][j - 1] == Status.MATCH ? 0 : 1);
				cost[i][j] = Math.min(substitute, Math.min(cost[i - 1][j], cost[i][j - 1]) + 1);
			}
		}

		Status[] expected = new Status[n];
		Status[] heard = new Status[m];
		int i = n;
		int j = m;

		while (i > 0 || j > 0) {
			if (i > 0 && j > 0 && cost[i][j] == cost[i - 1][j - 1] + (pair[i - 1][j - 1] == Status.MATCH ? 0 : 1)) {
				expected[i - 1] = pair[i - 1][j - 1];
				heard[j - 1] = pair[i - 1][j - 1];
				i--;
				j--;
			} else if (i > 0 && cost[i][j] == cost[i - 1][j] + 1) {
				expected[i - 1] = Status.MISSING;
				i--;
			} else {
				heard[j - 1] = Status.EXTRA;
				j--;
			}
		}

		return new Outcome(expected, heard, cost[n][m]);
	}
}
