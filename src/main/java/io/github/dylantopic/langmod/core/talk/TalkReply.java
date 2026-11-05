package io.github.dylantopic.langmod.core.talk;

/**
 * What a villager says back.
 *
 * @param text the reply in the language of the conversation
 * @param translation the reply in English, or null if the conversation is in English
 * @param suggestion something the player could say next, or null
 * @param mood how the villager sounds (and nods or shakes its head)
 * @param action what the villager does besides talking
 */
public record TalkReply(String text, String translation, Phrase suggestion, Mood mood, Action action) {
	public enum Mood {
		TALK,
		/** Agrees, or is pleased. */
		YES,
		/** Did not understand, or cannot help. */
		NO
	}

	public enum Action {
		NONE,
		/** The player pays one emerald and gets one bread. */
		SELL_BREAD
	}
}
