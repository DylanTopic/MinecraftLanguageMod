package io.github.dylantopic.langmod.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.dylantopic.langmod.core.text.CharReadings;
import io.github.dylantopic.langmod.core.text.TextNormalizer;

/**
 * Decides whether a transcription counts as saying the expected name.
 *
 * <ol>
 * <li>Exactly the expected characters: pass.</li>
 * <li>Otherwise both are compared as pinyin. Whisper writes words, not sounds, so it may pick a
 * homophone with other characters; a heard character matches an expected syllable if
 * <em>any</em> of its readings does. Syllables are aligned (edit distance), so a wrong, missing
 * or extra syllable is pinpointed instead of shifting everything after it.</li>
 * </ol>
 *
 * <p>Modes: strict (syllables and tones), toneless (syllables only), lenient (strict, but names of
 * 4+ syllables may have one error). Names over the configured length can be matched leniently in
 * any mode. An expected neutral tone accepts any tone.
 */
public final class Matcher {
	/** How one syllable compared. */
	public enum Status {
		/** Right syllable (and tone, if tones are checked). */
		MATCH,
		/** Right syllable, wrong tone. */
		TONE,
		/** Different syllable. */
		WRONG,
		/** Expected syllable with nothing heard for it. */
		MISSING,
		/** Heard syllable that does not belong. */
		EXTRA
	}

	/**
	 * One heard character (or Latin word), the reading chosen for display, and how it compared.
	 *
	 * @param reading null if the token has no pinyin (e.g. Latin letters)
	 */
	public record HeardToken(String text, Pinyin.Syllable reading, Status status) {
		public String pinyin() {
			return reading == null ? text : reading.marked();
		}
	}

	/**
	 * @param pass whether this counts as correct
	 * @param exactCharacters the characters matched exactly
	 * @param heard the normalized transcription
	 * @param heardTokens heard characters in order, with their status
	 * @param expectedStatus status of each expected syllable
	 * @param errors wrong, missing or extra syllables (wrong tones count when tones are checked)
	 * @param allowedErrors errors allowed for this name and mode
	 */
	public record Result(boolean pass, boolean exactCharacters, String heard, List<HeardToken> heardTokens,
			List<Status> expectedStatus, int errors, int allowedErrors, MatchMode mode) {
		public String heardPinyin() {
			List<String> parts = new ArrayList<>();

			for (HeardToken token : heardTokens) {
				parts.add(token.pinyin());
			}

			return String.join(" ", parts);
		}
	}

	private Matcher() {
	}

	/**
	 * @param heard a transcription already normalized by {@link TextNormalizer}
	 * @param longName the name is over the configured maximum length and gets lenient matching
	 */
	public static Result match(PinyinEntry expected, String heard, CharReadings readings, MatchMode mode, boolean longName) {
		List<Pinyin.Syllable> want = expected.syllables();
		boolean checkTones = mode != MatchMode.TONELESS;
		int allowed = (mode == MatchMode.LENIENT && want.size() >= 4) || longName ? 1 : 0;

		if (isExpectedRepeated(heard, expected.hanzi())) {
			List<HeardToken> tokens = new ArrayList<>();
			int i = 0;

			for (int cp : expected.hanzi().codePoints().toArray()) {
				tokens.add(new HeardToken(new String(Character.toChars(cp)), want.get(i++), Status.MATCH));
			}

			return new Result(true, true, heard, tokens, Collections.nCopies(want.size(), Status.MATCH), 0, allowed, mode);
		}

		List<Token> tokens = tokenize(heard, readings);
		int n = want.size();
		int m = tokens.size();

		// cost[i][j] = cheapest alignment of the first i expected syllables with the first j heard tokens.
		int[][] cost = new int[n + 1][m + 1];

		for (int i = 0; i <= n; i++) {
			cost[i][0] = i;
		}

		for (int j = 0; j <= m; j++) {
			cost[0][j] = j;
		}

		for (int i = 1; i <= n; i++) {
			for (int j = 1; j <= m; j++) {
				int substitute = cost[i - 1][j - 1] + (compare(want.get(i - 1), tokens.get(j - 1), checkTones) == Status.MATCH ? 0 : 1);
				int missing = cost[i - 1][j] + 1;
				int extra = cost[i][j - 1] + 1;
				cost[i][j] = Math.min(substitute, Math.min(missing, extra));
			}
		}

		// Walk back through the table to label every syllable (preferring substitutions).
		Status[] expectedStatus = new Status[n];
		HeardToken[] heardTokens = new HeardToken[m];
		int i = n;
		int j = m;

		while (i > 0 || j > 0) {
			if (i > 0 && j > 0) {
				Pinyin.Syllable syllable = want.get(i - 1);
				Token token = tokens.get(j - 1);
				Status status = compare(syllable, token, checkTones);

				if (cost[i][j] == cost[i - 1][j - 1] + (status == Status.MATCH ? 0 : 1)) {
					expectedStatus[i - 1] = status;
					heardTokens[j - 1] = new HeardToken(token.text, token.readingFor(syllable), status);
					i--;
					j--;
					continue;
				}
			}

			if (i > 0 && cost[i][j] == cost[i - 1][j] + 1) {
				expectedStatus[i - 1] = Status.MISSING;
				i--;
			} else {
				Token token = tokens.get(j - 1);
				heardTokens[j - 1] = new HeardToken(token.text, token.readings.isEmpty() ? null : token.readings.getFirst(), Status.EXTRA);
				j--;
			}
		}

		int errors = cost[n][m];
		boolean pass = m > 0 && errors <= allowed;
		return new Result(pass, false, heard, List.of(heardTokens), List.of(expectedStatus), errors, allowed, mode);
	}

	/** "苹果" or "苹果苹果" (Whisper sometimes repeats a short word). */
	private static boolean isExpectedRepeated(String heard, String expected) {
		if (heard.isEmpty() || expected.isEmpty() || heard.length() % expected.length() != 0) {
			return false;
		}

		for (int k = 0; k < heard.length(); k += expected.length()) {
			if (!heard.startsWith(expected, k)) {
				return false;
			}
		}

		return true;
	}

	private static Status compare(Pinyin.Syllable expected, Token token, boolean checkTones) {
		boolean sameBase = false;

		for (Pinyin.Syllable reading : token.readings) {
			if (reading.base().equals(expected.base())) {
				if (!checkTones || expected.tone() == 5 || reading.tone() == expected.tone()) {
					return Status.MATCH;
				}

				sameBase = true;
			}
		}

		return sameBase ? Status.TONE : Status.WRONG;
	}

	private static List<Token> tokenize(String heard, CharReadings readings) {
		List<Token> tokens = new ArrayList<>();
		StringBuilder latin = new StringBuilder();

		for (int cp : heard.codePoints().toArray()) {
			if (TextNormalizer.isCjk(cp)) {
				if (!latin.isEmpty()) {
					tokens.add(new Token(latin.toString(), List.of()));
					latin.setLength(0);
				}

				tokens.add(new Token(new String(Character.toChars(cp)), readings.of(cp)));
			} else {
				latin.appendCodePoint(cp);
			}
		}

		if (!latin.isEmpty()) {
			tokens.add(new Token(latin.toString(), List.of()));
		}

		return tokens;
	}

	/** A heard character (or Latin word) with all of its readings. */
	private record Token(String text, List<Pinyin.Syllable> readings) {
		/** The reading to show next to an expected syllable: the one that fits best. */
		Pinyin.Syllable readingFor(Pinyin.Syllable expected) {
			Pinyin.Syllable sameBase = null;

			for (Pinyin.Syllable reading : readings) {
				if (reading.equals(expected) || (reading.base().equals(expected.base()) && expected.tone() == 5)) {
					return reading;
				}

				if (sameBase == null && reading.base().equals(expected.base())) {
					sameBase = reading;
				}
			}

			if (sameBase != null) {
				return sameBase;
			}

			return readings.isEmpty() ? null : readings.getFirst();
		}
	}
}
