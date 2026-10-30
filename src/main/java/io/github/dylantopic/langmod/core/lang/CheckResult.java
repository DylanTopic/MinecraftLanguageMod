package io.github.dylantopic.langmod.core.lang;

import java.util.List;

import io.github.dylantopic.langmod.core.MatchMode;
import io.github.dylantopic.langmod.core.Matcher;
import io.github.dylantopic.langmod.core.Matcher.Status;

/**
 * Whether a transcription counts as saying the expected name, in a form shared by all languages.
 *
 * @param pass whether this counts as correct
 * @param exact the name was said exactly as written (no sound-alike spellings needed)
 * @param heard the normalized transcription
 * @param heardTokens what was heard, unit by unit, with each unit's status
 * @param expectedStatus status of each unit of the expected name ({@link Language#units})
 * @param errors wrong, missing or extra units (wrong tones count in Mandarin when tones are checked)
 * @param allowedErrors errors allowed for this name and mode
 * @param heardPronunciation pinyin or romanization of what was heard, empty if the language has none
 */
public record CheckResult(boolean pass, boolean exact, String heard, List<Token> heardTokens, List<Status> expectedStatus,
		int errors, int allowedErrors, MatchMode mode, String heardPronunciation) {
	public CheckResult {
		heardTokens = List.copyOf(heardTokens);
		expectedStatus = List.copyOf(expectedStatus);
	}

	/** One heard unit (character, syllable or word). */
	public record Token(String text, String pronunciation, Status status) {
	}

	/** The Mandarin matcher's result in the shared form. */
	public static CheckResult of(Matcher.Result result) {
		List<Token> tokens = result.heardTokens().stream()
				.map(token -> new Token(token.text(), token.pinyin(), token.status()))
				.toList();
		return new CheckResult(result.pass(), result.exactCharacters(), result.heard(), tokens, result.expectedStatus(),
				result.errors(), result.allowedErrors(), result.mode(), result.heardPinyin());
	}
}
