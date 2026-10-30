package io.github.dylantopic.langmod.core.lang;

/**
 * The name of one item in the language being learned.
 *
 * @param key the item's translation key, e.g. {@code item.minecraft.apple}
 * @param text the name, e.g. 苹果, Manzana or 사과
 * @param pronunciation shown under the name: pinyin with tone marks (Mandarin), Revised
 *     Romanization (Korean), or empty (Spanish, French)
 */
public record NameEntry(String key, String text, String pronunciation) {
}
