package io.github.dylantopic.langmod.core.talk;

/**
 * Something to say, in the language of the conversation.
 *
 * @param text in the language of the conversation
 * @param translation in English, or null if the conversation is in English
 */
public record Phrase(String text, String translation) {
}
