package io.github.dylantopic.langmod.core.lang;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import io.github.dylantopic.langmod.core.MatchMode;
import io.github.dylantopic.langmod.core.Matcher.Status;

/**
 * Korean: names are compared syllable by syllable, ignoring spaces (Whisper and Minecraft do not
 * always space compound words the same way). Syllables that sound alike count as the same (see
 * {@link Hangul#soundKey}). Names are shown with their Revised Romanization.
 */
public final class KoreanPack implements LanguagePack {
	private static final List<String> NOISE_MARKERS = List.of("시청해주셔서", "시청해 주셔서", "구독", "좋아요", "자막", "다음 영상");

	private final NameDictionary names;

	public KoreanPack(NameDictionary names) {
		this.names = names;
	}

	@Override
	public Language language() {
		return Language.KOREAN;
	}

	/**
	 * Names with Latin letters (only TNT) are left out, as in Mandarin: there is no Korean word to learn,
	 * and Whisper may write the letters either way (TNT or 티엔티).
	 */
	@Override
	public Optional<NameEntry> name(String translationKey) {
		return names.get(translationKey)
				.filter(text -> text.chars().noneMatch(c -> c < 0x80 && Character.isLetter(c)))
				.map(text -> new NameEntry(translationKey, text, KoreanRomanizer.romanize(text)));
	}

	@Override
	public int nameCount() {
		return names.size();
	}

	@Override
	public int overrideCount() {
		return names.overrideCount();
	}

	/** Keeps Hangul, letters and digits (lowercased) and single spaces. */
	@Override
	public String normalize(String transcript) {
		String text = Normalizer.normalize(transcript, Normalizer.Form.NFC);
		StringBuilder out = new StringBuilder();

		for (int cp : text.codePoints().toArray()) {
			if (Hangul.isSyllable(cp) || Character.isLetterOrDigit(cp)) {
				out.appendCodePoint(Character.toLowerCase(cp));
			} else if (!out.isEmpty() && out.charAt(out.length() - 1) != ' ') {
				out.append(' ');
			}
		}

		return out.toString().trim();
	}

	@Override
	public String pronounce(String normalized) {
		return KoreanRomanizer.romanize(normalized);
	}

	@Override
	public boolean isNoise(String normalized, NameEntry expected) {
		String heard = normalized.replace(" ", "");

		if (heard.contains(expected.text().replace(" ", ""))) {
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
		List<String> want = Language.KOREAN.units(expected.text());
		List<String> got = Language.KOREAN.units(normalized);
		int allowed = (mode == MatchMode.LENIENT && want.size() >= 4) || longName ? 1 : 0;
		Alignment.Outcome outcome = Alignment.align(want.size(), got.size(), (i, j) -> compare(want.get(i), got.get(j)));
		List<CheckResult.Token> tokens = new ArrayList<>();

		for (int j = 0; j < got.size(); j++) {
			tokens.add(new CheckResult.Token(got.get(j), KoreanRomanizer.romanize(got.get(j)), outcome.heard()[j]));
		}

		boolean exact = String.join("", got).equals(String.join("", want).toLowerCase(Locale.ROOT));
		boolean pass = !got.isEmpty() && outcome.errors() <= allowed;
		return new CheckResult(pass, exact, normalized, tokens, List.of(outcome.expected()), outcome.errors(), allowed, mode,
				pronounce(normalized));
	}

	private static Status compare(String expected, String heard) {
		if (expected.equalsIgnoreCase(heard)) {
			return Status.MATCH;
		}

		if (expected.length() == 1 && heard.length() == 1 && Hangul.soundKey(expected.charAt(0)) == Hangul.soundKey(heard.charAt(0))) {
			return Status.MATCH;
		}

		return Status.WRONG;
	}
}
