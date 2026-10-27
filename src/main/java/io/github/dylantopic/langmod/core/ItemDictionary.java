package io.github.dylantopic.langmod.core;

import java.io.Reader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Chinese names and pinyin of items, keyed by translation key.
 *
 * <p>The bundled table is generated from Minecraft's own zh_cn.json by tools/generate_pinyin.py.
 * A user override file with the same format takes priority; its entries may give only
 * {@code pinyin_numbers} (or only {@code pinyin_marks}) to fix a reading, or a full entry to add or
 * replace a name. Keys starting with {@code _} are ignored, so the file can carry comments.
 */
public final class ItemDictionary {
	private final Map<String, PinyinEntry> entries;
	private final int overrideCount;

	private ItemDictionary(Map<String, PinyinEntry> entries, int overrideCount) {
		this.entries = Map.copyOf(entries);
		this.overrideCount = overrideCount;
	}

	public static ItemDictionary empty() {
		return new ItemDictionary(Map.of(), 0);
	}

	/**
	 * @param bundled the bundled items.json
	 * @param overrides the user's pinyin_overrides.json, or null
	 * @param warn receives a message for every entry that is skipped because it is invalid
	 */
	public static ItemDictionary load(Reader bundled, Reader overrides, Consumer<String> warn) {
		Map<String, PinyinEntry> entries = new HashMap<>();
		read(JsonParser.parseReader(bundled).getAsJsonObject(), entries, warn);
		int overrideCount = 0;

		if (overrides != null) {
			overrideCount = read(JsonParser.parseReader(overrides).getAsJsonObject(), entries, warn);
		}

		return new ItemDictionary(entries, overrideCount);
	}

	public Optional<PinyinEntry> get(String translationKey) {
		return Optional.ofNullable(entries.get(translationKey));
	}

	public int size() {
		return entries.size();
	}

	public int overrideCount() {
		return overrideCount;
	}

	private static int read(JsonObject json, Map<String, PinyinEntry> into, Consumer<String> warn) {
		int count = 0;

		for (Map.Entry<String, JsonElement> e : json.entrySet()) {
			String key = e.getKey();

			if (key.startsWith("_")) {
				continue;
			}

			try {
				into.put(key, parseEntry(key, e.getValue().getAsJsonObject(), into.get(key)));
				count++;
			} catch (RuntimeException ex) {
				warn.accept("Skipping pinyin entry '" + key + "': " + ex.getMessage());
			}
		}

		return count;
	}

	private static PinyinEntry parseEntry(String key, JsonObject obj, PinyinEntry existing) {
		String hanzi = string(obj, "hanzi");

		if (hanzi == null) {
			if (existing == null) {
				throw new IllegalArgumentException("missing \"hanzi\"");
			}

			hanzi = existing.hanzi();
		}

		hanzi = hanzi.replaceAll("\\s+", "");
		String numbers = string(obj, "pinyin_numbers");
		String marks = string(obj, "pinyin_marks");
		List<Pinyin.Syllable> syllables;

		if (numbers != null) {
			syllables = Pinyin.parseAll(numbers);
		} else if (marks != null) {
			syllables = Pinyin.parseAll(marks);
		} else if (existing != null && existing.hanzi().equals(hanzi)) {
			syllables = existing.syllables();
		} else {
			throw new IllegalArgumentException("missing \"pinyin_numbers\"");
		}

		// Tone marks are always regenerated from the syllables so the two can never disagree.
		return PinyinEntry.of(key, hanzi, syllables);
	}

	private static String string(JsonObject obj, String member) {
		JsonElement element = obj.get(member);
		return element == null || element.isJsonNull() ? null : element.getAsString();
	}
}
