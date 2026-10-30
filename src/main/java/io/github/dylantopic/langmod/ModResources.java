package io.github.dylantopic.langmod;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import net.fabricmc.loader.api.FabricLoader;

import io.github.dylantopic.langmod.core.ItemDictionary;
import io.github.dylantopic.langmod.core.lang.Language;
import io.github.dylantopic.langmod.core.lang.NameDictionary;

/** Loads the data files bundled in the mod jar (item names, pinyin tables, OpenCC dictionaries). */
public final class ModResources {
	private static final String OVERRIDE_TEMPLATE = """
			{
				"_comment": "Fixes for the bundled pinyin. Entries here win over the mod's assets/langmod/pinyin/items.json (same format). Keys starting with _ are ignored: remove the _ in front of the example below to activate it, then run /langmod reload or restart.",
				"_item.minecraft.apple": {"hanzi": "苹果", "pinyin_numbers": "ping2 guo3"}
			}
			""";

	private ModResources() {
	}

	/** Opens a file from the mod jar, e.g. {@code assets/langmod/pinyin/items.json}. */
	public static BufferedReader open(String path) throws IOException {
		Path file = FabricLoader.getInstance().getModContainer(LangMod.MOD_ID)
				.flatMap(container -> container.findPath(path))
				.orElseThrow(() -> new IOException("Missing bundled resource " + path));
		return Files.newBufferedReader(file, StandardCharsets.UTF_8);
	}

	public static Path pinyinOverridesFile() {
		return LangModConfig.directory().resolve("pinyin_overrides.json");
	}

	/** {@code config/langmod/names_es_mx.json} and so on: the user's fixes to the bundled names of one language. */
	public static Path namesOverridesFile(Language language) {
		return LangModConfig.directory().resolve("names_" + language.id() + ".json");
	}

	/** Loads the bundled names of a language (Spanish, French, Korean) merged with the user's override file. */
	public static NameDictionary loadNames(Language language) {
		Path overrides = namesOverridesFile(language);
		createTemplate(overrides, """
				{
					"_comment": "Fixes for the bundled %s item names. Entries here win over the mod's assets/langmod/names/%s.json (same format). Keys starting with _ are ignored: remove the _ in front of the example below to activate it, then run /langmod reload or restart.",
					"_item.minecraft.apple": "..."
				}
				""".formatted(language.englishName(), language.id()));

		try (BufferedReader bundled = open("assets/langmod/names/" + language.id() + ".json")) {
			NameDictionary names;

			if (Files.exists(overrides)) {
				try (BufferedReader user = Files.newBufferedReader(overrides, StandardCharsets.UTF_8)) {
					names = NameDictionary.load(bundled, user, LangMod.LOGGER::warn);
				}
			} else {
				names = NameDictionary.load(bundled, null, LangMod.LOGGER::warn);
			}

			LangMod.LOGGER.info("Loaded {} {} item names ({} from {})", names.size(), language.englishName(), names.overrideCount(),
					overrides.getFileName());
			return names;
		} catch (IOException | RuntimeException e) {
			LangMod.LOGGER.error("Could not load the {} item names; challenges in that language are disabled", language.englishName(), e);
			return NameDictionary.empty();
		}
	}

	private static void createTemplate(Path file, String content) {
		try {
			if (Files.notExists(file)) {
				Files.createDirectories(file.getParent());
				Files.writeString(file, content, StandardCharsets.UTF_8);
			}
		} catch (IOException e) {
			LangMod.LOGGER.warn("Could not create {}: {}", file, e.getMessage());
		}
	}

	/** Loads the bundled item names merged with config/langmod/pinyin_overrides.json. */
	public static ItemDictionary loadItemDictionary() {
		Path overrides = pinyinOverridesFile();
		createTemplate(overrides, OVERRIDE_TEMPLATE);

		try (BufferedReader bundled = open("assets/langmod/pinyin/items.json")) {
			ItemDictionary dictionary;

			if (Files.exists(overrides)) {
				try (BufferedReader user = Files.newBufferedReader(overrides, StandardCharsets.UTF_8)) {
					dictionary = ItemDictionary.load(bundled, user, LangMod.LOGGER::warn);
				}
			} else {
				dictionary = ItemDictionary.load(bundled, null, LangMod.LOGGER::warn);
			}

			LangMod.LOGGER.info("Loaded {} Chinese item names ({} from {})", dictionary.size(), dictionary.overrideCount(), overrides.getFileName());
			return dictionary;
		} catch (IOException | RuntimeException e) {
			LangMod.LOGGER.error("Could not load the pinyin dictionary; challenges are disabled", e);
			return ItemDictionary.empty();
		}
	}
}
