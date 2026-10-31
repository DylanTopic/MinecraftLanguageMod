package io.github.dylantopic.langmod.core.lang;

import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Item names in one language, keyed by translation key ({@code "item.minecraft.apple": "Manzana"}).
 *
 * <p>The bundled files are extracted from Minecraft's own language files by tools/generate_names.py.
 * A user override file with the same format takes priority; keys starting with {@code _} are
 * ignored, so the file can carry comments.
 */
public final class NameDictionary {
	private final Map<String, String> names;
	private final int overrideCount;

	private NameDictionary(Map<String, String> names, int overrideCount) {
		this.names = Map.copyOf(names);
		this.overrideCount = overrideCount;
	}

	public static NameDictionary empty() {
		return new NameDictionary(Map.of(), 0);
	}

	/**
	 * @param overrides the user's override file, or null
	 * @param warn receives a message for every entry that is skipped because it is invalid
	 */
	public static NameDictionary load(Reader bundled, Reader overrides, Consumer<String> warn) {
		Map<String, String> names = new HashMap<>();
		read(JsonParser.parseReader(bundled).getAsJsonObject(), names, warn);
		int overrideCount = overrides == null ? 0 : read(JsonParser.parseReader(overrides).getAsJsonObject(), names, warn);
		return new NameDictionary(names, overrideCount);
	}

	public Optional<String> get(String translationKey) {
		return Optional.ofNullable(names.get(translationKey));
	}

	public int size() {
		return names.size();
	}

	/** Every entry (used for files that map names to readings rather than keys to names). */
	public Map<String, String> entries() {
		return names;
	}

	public int overrideCount() {
		return overrideCount;
	}

	private static int read(JsonObject json, Map<String, String> into, Consumer<String> warn) {
		int count = 0;

		for (Map.Entry<String, JsonElement> e : json.entrySet()) {
			if (e.getKey().startsWith("_")) {
				continue;
			}

			JsonElement value = e.getValue();

			if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() && !value.getAsString().isBlank()) {
				into.put(e.getKey(), value.getAsString().trim());
				count++;
			} else {
				warn.accept("Skipping name '" + e.getKey() + "': expected a non-empty string");
			}
		}

		return count;
	}
}
