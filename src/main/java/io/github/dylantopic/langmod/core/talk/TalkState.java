package io.github.dylantopic.langmod.core.talk;

/** What a villager remembers during one conversation. */
public final class TalkState {
	/** What the villager asked and is waiting to hear about. */
	enum Expecting {
		NOTHING,
		/** "How are you?" */
		FEELING,
		/** "What is your favorite color?" */
		COLOR,
		/** "It costs one emerald." */
		PAYMENT
	}

	/** The name the player gave ("My name is ..."), or null. */
	String playerName;
	/** Whether the villager has said "Nice to meet you". */
	boolean met;
	boolean saidGoodbye;
	Expecting expecting = Expecting.NOTHING;
	/** Replies in a row that were "I do not understand". */
	int misunderstood;

	/** The name the player gave, or null if they have not said it. */
	public String playerName() {
		return playerName;
	}

	public int misunderstood() {
		return misunderstood;
	}
}
