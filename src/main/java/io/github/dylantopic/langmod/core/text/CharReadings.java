package io.github.dylantopic.langmod.core.text;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.dylantopic.langmod.core.Pinyin;

/**
 * Every reading of every Chinese character (from pypinyin, bundled as chars.tsv). Used to turn
 * whatever Whisper transcribed into pinyin. Characters with several readings (长 cháng/zhǎng) keep
 * all of them, most common first, so matching can accept any plausible reading.
 */
public final class CharReadings {
	private final Map<Integer, List<Pinyin.Syllable>> readings;

	private CharReadings(Map<Integer, List<Pinyin.Syllable>> readings) {
		this.readings = Map.copyOf(readings);
	}

	public static CharReadings load(Reader reader) throws IOException {
		Map<Integer, List<Pinyin.Syllable>> map = new HashMap<>();
		BufferedReader lines = reader instanceof BufferedReader br ? br : new BufferedReader(reader);
		String line;

		while ((line = lines.readLine()) != null) {
			int tab = line.indexOf('\t');

			if (line.startsWith("#") || tab <= 0) {
				continue;
			}

			List<Pinyin.Syllable> syllables = new ArrayList<>();

			for (String reading : line.substring(tab + 1).split(",")) {
				try {
					syllables.add(Pinyin.parse(reading));
				} catch (IllegalArgumentException e) {
					// Not plain pinyin (e.g. an interjection); irrelevant for item names.
				}
			}

			if (!syllables.isEmpty()) {
				map.put(line.codePointAt(0), List.copyOf(syllables));
			}
		}

		return new CharReadings(map);
	}

	/** All readings of a character, most common first; empty if unknown (not a CJK character). */
	public List<Pinyin.Syllable> of(int codePoint) {
		return readings.getOrDefault(codePoint, List.of());
	}

	public int size() {
		return readings.size();
	}
}
