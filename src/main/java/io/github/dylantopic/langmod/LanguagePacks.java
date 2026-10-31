package io.github.dylantopic.langmod;

import java.util.EnumMap;
import java.util.Map;

import io.github.dylantopic.langmod.core.lang.Language;
import io.github.dylantopic.langmod.core.lang.LanguagePack;
import io.github.dylantopic.langmod.core.lang.MandarinPack;

/** The language packs (names, matching, pronunciation), loaded on first use and reloaded by /langmod reload. */
public final class LanguagePacks {
	private static final Map<Language, LanguagePack> PACKS = new EnumMap<>(Language.class);

	private LanguagePacks() {
	}

	public static synchronized LanguagePack get(Language language) {
		return PACKS.computeIfAbsent(language, LanguagePacks::load);
	}

	/** Re-reads the bundled names and the user's override files on next use. */
	public static synchronized void reload() {
		PACKS.clear();
	}

	private static LanguagePack load(Language language) {
		if (language == Language.MANDARIN) {
			return new MandarinPack(ModResources.loadItemDictionary(), TextData.get().normalizer(), TextData.get().readings());
		}

		return LanguagePack.forNames(language, ModResources.loadNames(language),
				language == Language.JAPANESE ? ModResources.loadJapaneseReadings() : Map.of());
	}
}
