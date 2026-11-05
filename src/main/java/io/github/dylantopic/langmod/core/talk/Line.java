package io.github.dylantopic.langmod.core.talk;

import java.util.List;
import java.util.stream.Collectors;

import io.github.dylantopic.langmod.core.lang.Language;

/**
 * Something a villager says, in every language villagers speak.
 *
 * @param next what the player could say back, or null
 */
record Line(String english, String korean, Line next) {
	static Line of(String english, String korean) {
		return new Line(english, korean, null);
	}

	/** The same line, with what the player could say back. */
	Line then(String english, String korean) {
		return new Line(this.english, this.korean, of(english, korean));
	}

	String in(Language language) {
		return language == Language.KOREAN ? korean : english;
	}

	/** In the language of the conversation, with the English translation unless that is the language. */
	Phrase phrase(Language language) {
		return new Phrase(in(language), language == Language.ENGLISH ? null : english);
	}

	static Line join(List<Line> lines) {
		return new Line(lines.stream().map(Line::english).collect(Collectors.joining(" ")),
				lines.stream().map(Line::korean).collect(Collectors.joining(" ")), lines.getLast().next());
	}
}
