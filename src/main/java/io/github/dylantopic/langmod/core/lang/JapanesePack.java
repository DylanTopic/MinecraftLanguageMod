package io.github.dylantopic.langmod.core.lang;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import io.github.dylantopic.langmod.core.MatchMode;
import io.github.dylantopic.langmod.core.Matcher.Status;

/**
 * Japanese: names are compared character by character, katakana and hiragana counting as the same
 * (Whisper writes かぼちゃ or カボチャ). An answer in kana also counts for a name written with kanji
 * when it matches the name's reading (たいまつ for 松明); long vowel marks and small っ are not
 * counted there, as they are easy to blur. Names with kanji are shown with their reading, every
 * name with its romaji.
 */
public final class JapanesePack implements LanguagePack {
	private static final List<String> NOISE_MARKERS = List.of("ご視聴ありがとうございました", "ご視聴ありがとうございます", "チャンネル登録", "字幕");

	private final NameDictionary names;
	private final Map<String, String> readings;

	/** @param readings name -> its reading in hiragana, words separated by spaces */
	public JapanesePack(NameDictionary names, Map<String, String> readings) {
		this.names = names;
		this.readings = readings;
	}

	@Override
	public Language language() {
		return Language.JAPANESE;
	}

	@Override
	public Optional<NameEntry> name(String translationKey) {
		return names.get(translationKey).map(text -> new NameEntry(translationKey, text, pronunciation(text)));
	}

	@Override
	public int nameCount() {
		return names.size();
	}

	@Override
	public int overrideCount() {
		return names.overrideCount();
	}

	/** Full-width letters and half-width kana made normal (NFKC); punctuation and spaces removed. */
	@Override
	public String normalize(String transcript) {
		StringBuilder out = new StringBuilder();

		for (int cp : Normalizer.normalize(transcript, Normalizer.Form.NFKC).codePoints().toArray()) {
			if (Character.isLetterOrDigit(cp) || JapaneseRomanizer.isKana(cp)) {
				out.appendCodePoint(Character.toLowerCase(cp));
			}
		}

		return out.toString();
	}

	/** Romaji of a normalized transcription; kanji are kept unless it is a known name. */
	@Override
	public String pronounce(String normalized) {
		String romaji = JapaneseRomanizer.romanize(readings.getOrDefault(normalized, normalized));
		return romaji.equals(normalized) ? "" : romaji;
	}

	@Override
	public boolean isNoise(String normalized, NameEntry expected) {
		if (fold(normalized).contains(fold(expected.text()))) {
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
		String name = expected.text();
		String reading = readings.get(name);
		List<String> want = Language.JAPANESE.units(name);
		List<String> got = Language.JAPANESE.units(normalized);
		Alignment.Outcome bySurface = Alignment.align(want.size(), got.size(),
				(i, j) -> fold(want.get(i)).equals(fold(got.get(j))) ? Status.MATCH : Status.WRONG);
		List<String> wantKana = reading == null ? List.of() : kanaUnits(reading);
		List<String> gotKana = kanaUnits(normalized);
		boolean kanaAnswer = reading != null && !normalized.isEmpty() && normalized.codePoints().allMatch(JapaneseRomanizer::isKana);
		Alignment.Outcome byReading = kanaAnswer
				? Alignment.align(wantKana.size(), gotKana.size(), (i, j) -> wantKana.get(i).equals(gotKana.get(j)) ? Status.MATCH : Status.WRONG)
				: null;
		boolean useReading = byReading != null && byReading.errors() < bySurface.errors();
		int errors = useReading ? byReading.errors() : bySurface.errors();
		int allowed = (mode == MatchMode.LENIENT && want.size() >= 4) || longName ? 1 : 0;
		String heard = fold(normalized);
		boolean exact = heard.equals(fold(name)) || (reading != null && heard.equals(fold(reading)));
		boolean pass = !normalized.isEmpty() && (exact || errors <= allowed);

		List<String> tokenTexts = useReading ? gotKana : got;
		Status[] tokenStatuses = useReading ? byReading.heard() : bySurface.heard();
		List<CheckResult.Token> tokens = new ArrayList<>();

		for (int j = 0; j < tokenTexts.size(); j++) {
			tokens.add(new CheckResult.Token(tokenTexts.get(j), pronounce(tokenTexts.get(j)), tokenStatuses[j]));
		}

		// The marks under the name are per character of the name; a kana answer that passed marks it all right.
		List<Status> expectedStatus = useReading && pass ? Collections.nCopies(want.size(), Status.MATCH) : List.of(bySurface.expected());
		return new CheckResult(pass, exact, normalized, tokens, expectedStatus, errors, allowed, mode, pronounce(normalized));
	}

	/** Japanese lists sort by reading (in kana order), not by the characters' code points. */
	@Override
	public String sortKey(NameEntry entry) {
		return fold(readings.getOrDefault(entry.text(), entry.text()));
	}

	/** A name with kanji is said from its reading, so the voice says what the popup shows (松明 as たいまつ). */
	@Override
	public String spokenText(NameEntry entry) {
		String reading = readings.get(entry.text());
		return reading != null && hasKanji(entry.text()) ? reading.replace(" ", "") : entry.text();
	}

	private static boolean hasKanji(String text) {
		return text.codePoints().anyMatch(cp -> Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN);
	}

	/** "たいまつ  taimatsu" for a name with kanji, "ringo" for one in kana. */
	private String pronunciation(String text) {
		String reading = readings.get(text);

		if (reading == null) {
			return pronounce(text);
		}

		String romaji = JapaneseRomanizer.romanize(reading);
		return hasKanji(text) ? reading.replace(" ", "") + "  " + romaji : romaji;
	}

	/** Hiragana, lowercase, without spaces: the form names and answers are compared in. */
	private static String fold(String text) {
		return JapaneseRomanizer.toHiragana(Normalizer.normalize(text, Normalizer.Form.NFKC)).toLowerCase(Locale.ROOT).replace(" ", "");
	}

	/** Kana as hiragana, one per unit, without long vowel marks and small っ. */
	private static List<String> kanaUnits(String kana) {
		List<String> units = new ArrayList<>();

		for (int cp : JapaneseRomanizer.toHiragana(kana).codePoints().toArray()) {
			if (cp != 'ー' && cp != 'っ' && !Character.isWhitespace(cp)) {
				units.add(Character.toString(cp));
			}
		}

		return units;
	}
}
