package io.github.dylantopic.langmod.core.text;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

/**
 * Traditional -> Simplified Chinese conversion using OpenCC's dictionaries (TSPhrases.txt and
 * TSCharacters.txt, bundled with the mod). Like OpenCC's t2s, it replaces the longest matching
 * phrase first and falls back to single characters. Text that is already simplified is unchanged.
 */
public final class ChineseConverter {
	private final Map<String, String> phrases;
	private final Map<Integer, String> characters;
	private final int maxPhraseLength;

	private ChineseConverter(Map<String, String> phrases, Map<Integer, String> characters) {
		this.phrases = Map.copyOf(phrases);
		this.characters = Map.copyOf(characters);
		this.maxPhraseLength = phrases.keySet().stream().mapToInt(s -> s.codePointCount(0, s.length())).max().orElse(1);
	}

	public static ChineseConverter load(Reader phrasesFile, Reader charactersFile) throws IOException {
		Map<String, String> phrases = new HashMap<>();
		Map<Integer, String> characters = new HashMap<>();
		read(phrasesFile, (from, to) -> phrases.put(from, to));
		read(charactersFile, (from, to) -> {
			if (from.codePointCount(0, from.length()) == 1) {
				characters.put(from.codePointAt(0), to);
			}
		});
		return new ChineseConverter(phrases, characters);
	}

	public String toSimplified(String text) {
		StringBuilder out = new StringBuilder(text.length());
		int i = 0;

		outer:
		while (i < text.length()) {
			// Longest phrase first.
			int end = i;

			for (int n = 0; n < maxPhraseLength && end < text.length(); n++) {
				end = text.offsetByCodePoints(end, 1);
			}

			while (end > i && text.codePointCount(i, end) >= 2) {
				String replacement = phrases.get(text.substring(i, end));

				if (replacement != null) {
					out.append(replacement);
					i = end;
					continue outer;
				}

				end = text.offsetByCodePoints(end, -1);
			}

			int codePoint = text.codePointAt(i);
			String replacement = characters.get(codePoint);

			if (replacement != null) {
				out.append(replacement);
			} else {
				out.appendCodePoint(codePoint);
			}

			i += Character.charCount(codePoint);
		}

		return out.toString();
	}

	private interface Entry {
		void accept(String from, String to);
	}

	/** OpenCC format: {@code source<TAB>target1 target2 ...}; the first target is the default. */
	private static void read(Reader reader, Entry entry) throws IOException {
		BufferedReader lines = reader instanceof BufferedReader br ? br : new BufferedReader(reader);
		String line;

		while ((line = lines.readLine()) != null) {
			int tab = line.indexOf('\t');

			if (line.isEmpty() || line.startsWith("#") || tab <= 0) {
				continue;
			}

			String targets = line.substring(tab + 1).trim();
			int space = targets.indexOf(' ');
			entry.accept(line.substring(0, tab), space < 0 ? targets : targets.substring(0, space));
		}
	}
}
