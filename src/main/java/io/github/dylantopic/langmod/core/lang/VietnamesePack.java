package io.github.dylantopic.langmod.core.lang;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import io.github.dylantopic.langmod.core.MatchMode;
import io.github.dylantopic.langmod.core.Matcher.Status;

/**
 * Vietnamese: names are compared syllable by syllable (Vietnamese writes a space after each), and
 * each syllable's tone is checked like a Mandarin one: the right letters with another tone mark is a
 * wrong tone, unless the match mode is toneless. Where a tone mark sits (hòa or hoà) and i or y
 * (kĩ or kỹ) do not matter, and a classifier in front ("quả táo", "cái bánh mì") is ignored.
 * Final consonants that many speakers (and Whisper) do not tell apart count as the same: the
 * unreleased stops -t, -c, -ch, -p (đất/đấc) and the nasals -n, -ng, -nh.
 */
public final class VietnamesePack implements LanguagePack {
	private static final List<String> CLASSIFIERS = List.of("quả", "trái", "cái", "con", "chiếc", "cục", "một", "những", "các");
	private static final List<String> NOISE_MARKERS = List.of("subscribe", "ghiền mì gõ", "đăng ký kênh", "cảm ơn các bạn đã theo dõi",
			"hẹn gặp lại các bạn");
	/** Combining marks of the five tones after the level one: sắc, huyền, hỏi, ngã, nặng. */
	private static final String TONE_MARKS = "̣́̀̉̃";
	private static final Locale VIETNAMESE = Locale.forLanguageTag("vi");

	private final NameDictionary names;

	public VietnamesePack(NameDictionary names) {
		this.names = names;
	}

	/**
	 * @param sound the letters with the final consonants that sound alike merged
	 * @param tone 0 for the level tone, otherwise 1 + the index of its mark in {@link #TONE_MARKS}
	 */
	record Syllable(String letters, String sound, int tone) {
	}

	@Override
	public Language language() {
		return Language.VIETNAMESE;
	}

	@Override
	public Optional<NameEntry> name(String translationKey) {
		return names.get(translationKey).map(text -> new NameEntry(translationKey, text, ""));
	}

	@Override
	public int nameCount() {
		return names.size();
	}

	@Override
	public int overrideCount() {
		return names.overrideCount();
	}

	/** Lowercase syllables separated by single spaces, punctuation removed. */
	@Override
	public String normalize(String transcript) {
		StringBuilder out = new StringBuilder();

		for (int cp : Normalizer.normalize(transcript, Normalizer.Form.NFC).toLowerCase(VIETNAMESE).codePoints().toArray()) {
			if (Character.isLetterOrDigit(cp)) {
				out.appendCodePoint(cp);
			} else if (!out.isEmpty() && out.charAt(out.length() - 1) != ' ') {
				out.append(' ');
			}
		}

		return out.toString().trim();
	}

	@Override
	public String pronounce(String normalized) {
		return "";
	}

	@Override
	public boolean isNoise(String normalized, NameEntry expected) {
		if (normalized.contains(normalize(expected.text()))) {
			return false;
		}

		for (String marker : NOISE_MARKERS) {
			if (normalized.contains(marker)) {
				return true;
			}
		}

		return false;
	}

	@Override
	public CheckResult check(NameEntry expected, String normalized, MatchMode mode, boolean longName) {
		List<String> want = Language.VIETNAMESE.units(normalize(expected.text()));
		List<String> heard = Language.VIETNAMESE.units(normalized);
		int classifiers = leadingClassifiers(heard, want);
		List<String> compared = heard.subList(classifiers, heard.size());
		Alignment.Outcome outcome = Alignment.align(want.size(), compared.size(), (i, j) -> compare(want.get(i), compared.get(j), mode));
		List<CheckResult.Token> tokens = new ArrayList<>();

		for (int j = 0; j < heard.size(); j++) {
			tokens.add(new CheckResult.Token(heard.get(j), "", j < classifiers ? Status.MATCH : outcome.heard()[j - classifiers]));
		}

		int allowed = (mode == MatchMode.LENIENT && want.size() >= 4) || longName ? 1 : 0;
		boolean exact = String.join(" ", compared).equals(String.join(" ", want));
		boolean pass = !compared.isEmpty() && outcome.errors() <= allowed;
		return new CheckResult(pass, exact, normalized, tokens, List.of(outcome.expected()), outcome.errors(), allowed, mode, "");
	}

	static Status compare(String expected, String heard, MatchMode mode) {
		Syllable e = syllable(expected);
		Syllable h = syllable(heard);

		if (!e.letters().equals(h.letters()) && !e.sound().equals(h.sound())) {
			return Status.WRONG;
		}

		return e.tone() == h.tone() || mode == MatchMode.TONELESS ? Status.MATCH : Status.TONE;
	}

	/** The letters (with their vowel marks, as in ă â ê ô ơ ư) and the tone of a lowercase syllable. */
	static Syllable syllable(String text) {
		StringBuilder letters = new StringBuilder();
		int tone = 0;

		for (char ch : Normalizer.normalize(text, Normalizer.Form.NFD).toCharArray()) {
			int mark = TONE_MARKS.indexOf(ch);

			if (mark >= 0) {
				tone = mark + 1;
			} else {
				letters.append(ch);
			}
		}

		// A final y after a consonant is the same vowel as i (kỹ = kĩ, lý = lí).
		String plain = Normalizer.normalize(letters, Normalizer.Form.NFC).replaceAll("^([bcdđghklmnpqrstvx]+)y$", "$1i");
		return new Syllable(plain, plain.replaceAll("(ch|c|p|t)$", "K").replaceAll("(ng|nh|n)$", "N"), tone);
	}

	/** How many words at the start are classifiers not part of the name ("quả táo" for Táo). */
	private static int leadingClassifiers(List<String> heard, List<String> want) {
		String first = want.isEmpty() ? "" : want.getFirst();
		int count = 0;

		while (count < heard.size() - 1 && count < 2 && CLASSIFIERS.contains(heard.get(count)) && !heard.get(count).equals(first)) {
			count++;
		}

		return count;
	}
}
