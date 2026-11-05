package io.github.dylantopic.langmod.core.talk;

/** Understands what the player says in one language, by looking for the words that matter. */
interface Ears {
	/** What one thing the player said is about, given what the villager is waiting to hear. */
	Heard hear(String said, TalkState state);
}
