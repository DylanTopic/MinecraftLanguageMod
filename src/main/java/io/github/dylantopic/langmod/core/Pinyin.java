package io.github.dylantopic.langmod.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pinyin syllables: parsing tone-number notation ({@code "lv4"}, {@code "lü4"}, {@code "lu:4"},
 * {@code "de5"}) or tone marks ({@code "lǜ"}), and rendering tone marks.
 */
public final class Pinyin {
	/** Tone-marked vowels; row = a, e, i, o, u, ü and column = tone 1-4. */
	private static final String[] MARKS = {"āáǎà", "ēéěè", "īíǐì", "ōóǒò", "ūúǔù", "ǖǘǚǜ"};
	private static final String PLAIN = "aeiouv";

	private Pinyin() {
	}

	/**
	 * One syllable. {@code base} is lower case a-z with {@code v} standing for ü (e.g. {@code "lv"});
	 * {@code tone} is 1-4, or 5 for the neutral tone.
	 */
	public record Syllable(String base, int tone) {
		public Syllable {
			if (base.isEmpty() || !base.chars().allMatch(c -> c >= 'a' && c <= 'z')) {
				throw new IllegalArgumentException("Invalid pinyin syllable '" + base + "'");
			}

			if (tone < 1 || tone > 5) {
				throw new IllegalArgumentException("Invalid tone " + tone + " for '" + base + "'");
			}
		}

		public String numbered() {
			return base + tone;
		}

		public String marked() {
			return mark(base, tone);
		}

		@Override
		public String toString() {
			return numbered();
		}
	}

	/** Parses one syllable in tone-number or tone-mark notation. A missing tone means neutral (5). */
	public static Syllable parse(String text) {
		String s = text.trim().toLowerCase(Locale.ROOT).replace("u:", "v").replace('ü', 'v');

		if (s.isEmpty()) {
			throw new IllegalArgumentException("Empty pinyin syllable");
		}

		int tone = 5;
		char last = s.charAt(s.length() - 1);

		if (last >= '0' && last <= '5') {
			tone = last == '0' ? 5 : last - '0';
			s = s.substring(0, s.length() - 1);
		} else {
			StringBuilder plain = new StringBuilder(s.length());

			for (int i = 0; i < s.length(); i++) {
				char c = s.charAt(i);
				int markedTone = 0;

				for (int row = 0; row < MARKS.length && markedTone == 0; row++) {
					int column = MARKS[row].indexOf(c);

					if (column >= 0) {
						markedTone = column + 1;
						c = PLAIN.charAt(row);
					}
				}

				if (markedTone != 0) {
					tone = markedTone;
				}

				plain.append(c);
			}

			s = plain.toString();
		}

		return new Syllable(s, tone);
	}

	/** Parses space-separated syllables, e.g. {@code "ping2 guo3"} or {@code "píng guǒ"}. */
	public static List<Syllable> parseAll(String text) {
		List<Syllable> result = new ArrayList<>();

		for (String part : text.trim().split("\\s+")) {
			if (!part.isEmpty()) {
				result.add(parse(part));
			}
		}

		return result;
	}

	public static String marked(List<Syllable> syllables) {
		StringBuilder sb = new StringBuilder();

		for (Syllable syllable : syllables) {
			if (!sb.isEmpty()) {
				sb.append(' ');
			}

			sb.append(syllable.marked());
		}

		return sb.toString();
	}

	public static String numbered(List<Syllable> syllables) {
		StringBuilder sb = new StringBuilder();

		for (Syllable syllable : syllables) {
			if (!sb.isEmpty()) {
				sb.append(' ');
			}

			sb.append(syllable.numbered());
		}

		return sb.toString();
	}

	/**
	 * Puts the tone mark on the right vowel: {@code a} or {@code e} if present, the {@code o} of
	 * {@code ou}, otherwise the last vowel.
	 */
	static String mark(String base, int tone) {
		String display = base.replace('v', 'ü');

		if (tone < 1 || tone > 4) {
			return display;
		}

		int index = base.indexOf('a');

		if (index < 0) {
			index = base.indexOf('e');
		}

		if (index < 0) {
			index = base.indexOf("ou");
		}

		if (index < 0) {
			for (int i = base.length() - 1; i >= 0; i--) {
				if ("iouv".indexOf(base.charAt(i)) >= 0) {
					index = i;
					break;
				}
			}
		}

		if (index < 0) {
			// Syllabic consonants such as "m" or "ng" carry no written mark here.
			return display;
		}

		char marked = MARKS[PLAIN.indexOf(base.charAt(index))].charAt(tone - 1);
		return display.substring(0, index) + marked + display.substring(index + 1);
	}
}
