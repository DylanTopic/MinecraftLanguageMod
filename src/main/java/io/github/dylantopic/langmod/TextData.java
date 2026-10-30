package io.github.dylantopic.langmod;

import java.io.BufferedReader;
import java.io.IOException;

import io.github.dylantopic.langmod.core.text.CharReadings;
import io.github.dylantopic.langmod.core.text.ChineseConverter;
import io.github.dylantopic.langmod.core.text.TextNormalizer;

/**
 * Mandarin text tables (used through {@link LanguagePacks}): OpenCC Traditional -> Simplified
 * conversion and the reading of every character. Loaded once; /langmod reload only reloads names.
 */
public final class TextData {
	private static volatile TextData instance;

	private final TextNormalizer normalizer;
	private final CharReadings readings;

	private TextData(TextNormalizer normalizer, CharReadings readings) {
		this.normalizer = normalizer;
		this.readings = readings;
	}

	public static TextData get() {
		TextData data = instance;

		if (data == null) {
			synchronized (TextData.class) {
				if (instance == null) {
					instance = load();
				}

				data = instance;
			}
		}

		return data;
	}

	private static TextData load() {
		try (BufferedReader phrases = ModResources.open("assets/langmod/opencc/TSPhrases.txt");
				BufferedReader characters = ModResources.open("assets/langmod/opencc/TSCharacters.txt");
				BufferedReader chars = ModResources.open("assets/langmod/pinyin/chars.tsv")) {
			TextData data = new TextData(new TextNormalizer(ChineseConverter.load(phrases, characters)), CharReadings.load(chars));
			LangMod.LOGGER.info("Loaded OpenCC tables and readings of {} characters", data.readings.size());
			return data;
		} catch (IOException e) {
			throw new IllegalStateException("Bundled text tables are missing from the mod jar", e);
		}
	}

	public TextNormalizer normalizer() {
		return normalizer;
	}

	public CharReadings readings() {
		return readings;
	}
}
