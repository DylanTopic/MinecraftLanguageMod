package io.github.dylantopic.langmod.core;

import java.util.List;

/**
 * The Chinese name of one item: its characters and one pinyin syllable per character.
 *
 * @param key the item's translation key, e.g. {@code item.minecraft.apple}
 * @param hanzi the name in Simplified characters, e.g. 苹果
 * @param marks pinyin with tone marks, e.g. {@code píng guǒ}
 * @param syllables one syllable per character
 */
public record PinyinEntry(String key, String hanzi, String marks, List<Pinyin.Syllable> syllables) {
	public PinyinEntry {
		syllables = List.copyOf(syllables);
		int characters = hanzi.codePointCount(0, hanzi.length());

		if (characters != syllables.size()) {
			throw new IllegalArgumentException(
					key + ": '" + hanzi + "' has " + characters + " characters but " + syllables.size() + " pinyin syllables");
		}
	}

	public static PinyinEntry of(String key, String hanzi, List<Pinyin.Syllable> syllables) {
		return new PinyinEntry(key, hanzi, Pinyin.marked(syllables), syllables);
	}

	/** Number of characters (= number of syllables). */
	public int length() {
		return syllables.size();
	}

	public String numbers() {
		return Pinyin.numbered(syllables);
	}
}
