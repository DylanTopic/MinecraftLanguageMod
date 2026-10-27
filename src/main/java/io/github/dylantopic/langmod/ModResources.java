package io.github.dylantopic.langmod;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import net.fabricmc.loader.api.FabricLoader;

import io.github.dylantopic.langmod.core.ItemDictionary;

/** Loads the data files bundled in the mod jar (pinyin tables, OpenCC dictionaries). */
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

	/** Loads the bundled item names merged with config/langmod/pinyin_overrides.json. */
	public static ItemDictionary loadItemDictionary() {
		Path overrides = pinyinOverridesFile();

		try {
			if (Files.notExists(overrides)) {
				Files.createDirectories(overrides.getParent());
				Files.writeString(overrides, OVERRIDE_TEMPLATE, StandardCharsets.UTF_8);
			}
		} catch (IOException e) {
			LangMod.LOGGER.warn("Could not create {}: {}", overrides, e.getMessage());
		}

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
