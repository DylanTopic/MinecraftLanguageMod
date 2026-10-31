package io.github.dylantopic.langmod.core.lang;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.github.dylantopic.langmod.core.MatchMode;
import io.github.dylantopic.langmod.core.Matcher.Status;

/**
 * Languages written with spaces between words (Spanish, French, Russian, Dutch, Tagalog, Bosnian,
 * Portuguese): names are compared word by word, ignoring case, punctuation, apostrophes, accents
 * and an article in front ("la manzana" for Manzana, "l'arc" for Arc). A word that is spelled
 * differently but sounds the same still matches (see {@link WordRules#soundKey}). Russian names
 * are shown with a transliteration.
 */
public final class WordPack implements LanguagePack {
	private final Language language;
	private final WordRules rules;
	private final NameDictionary names;

	public WordPack(Language language, WordRules rules, NameDictionary names) {
		this.language = language;
		this.rules = rules;
		this.names = names;
	}

	@Override
	public Language language() {
		return language;
	}

	@Override
	public Optional<NameEntry> name(String translationKey) {
		return names.get(translationKey).map(text -> new NameEntry(translationKey, text, rules.romanize(text)));
	}

	@Override
	public int nameCount() {
		return names.size();
	}

	@Override
	public int overrideCount() {
		return names.overrideCount();
	}

	/** Lowercase words separated by single spaces; apostrophes and hyphens inside words are kept. */
	@Override
	public String normalize(String transcript) {
		String text = language.lowercase(Normalizer.normalize(transcript, Normalizer.Form.NFC));
		StringBuilder out = new StringBuilder();

		for (int cp : text.codePoints().toArray()) {
			if (Character.isLetterOrDigit(cp)) {
				out.appendCodePoint(cp);
			} else if (cp == '\'' || cp == '’' || cp == '‘' || cp == 'ʼ') {
				out.append('\'');
			} else if (cp == '-') {
				out.append('-');
			} else {
				out.append(' ');
			}
		}

		List<String> words = new ArrayList<>();

		for (String word : out.toString().split(" +")) {
			String trimmed = word.replaceAll("^['-]+|['-]+$", "");

			if (!trimmed.isEmpty()) {
				words.add(trimmed);
			}
		}

		return String.join(" ", words);
	}

	@Override
	public String pronounce(String normalized) {
		return rules.romanize(normalized);
	}

	@Override
	public boolean isNoise(String normalized, NameEntry expected) {
		if (normalized.contains(normalize(expected.text()))) {
			return false;
		}

		for (String marker : rules.noiseMarkers()) {
			if (normalized.contains(marker)) {
				return true;
			}
		}

		return false;
	}

	@Override
	public CheckResult check(NameEntry expected, String normalized, MatchMode mode, boolean longName) {
		List<String> want = language.units(expected.text());
		List<String> heard = language.units(normalized);
		int articles = leadingArticles(heard, want);
		List<String> compared = new ArrayList<>(heard.subList(articles, heard.size()));

		if (articles == 0 && !compared.isEmpty()) {
			compared.set(0, withoutElision(compared.getFirst(), want)); // "l'arc" -> "arc"
		}

		int allowed = (mode == MatchMode.LENIENT && want.size() >= 4) || longName ? 1 : 0;
		Alignment.Outcome outcome = Alignment.align(want.size(), compared.size(), (i, j) -> compare(want.get(i), compared.get(j)));
		List<CheckResult.Token> tokens = new ArrayList<>();

		for (int j = 0; j < heard.size(); j++) {
			tokens.add(new CheckResult.Token(heard.get(j), rules.romanize(heard.get(j)), j < articles ? Status.MATCH : outcome.heard()[j - articles]));
		}

		boolean exact = compared.size() == want.size();

		for (int i = 0; exact && i < want.size(); i++) {
			exact = fold(want.get(i)).equals(fold(compared.get(i)));
		}

		boolean pass = !compared.isEmpty() && outcome.errors() <= allowed;
		return new CheckResult(pass, exact, normalized, tokens, List.of(outcome.expected()), outcome.errors(), allowed, mode, pronounce(normalized));
	}

	/** How many words at the start are articles not part of the name ("de la" counts as two). */
	private int leadingArticles(List<String> heard, List<String> want) {
		String firstWanted = want.isEmpty() ? "" : fold(want.getFirst());
		int count = 0;

		while (count < heard.size() - 1 && count < 2 && rules.articles().contains(heard.get(count)) && !heard.get(count).equals(firstWanted)) {
			count++;
		}

		return count;
	}

	/** Drops an elided article ("l'", "d'") the name itself does not start with. */
	private String withoutElision(String word, List<String> want) {
		int apostrophe = word.indexOf('\'');

		if (apostrophe == 1 && "ld".indexOf(word.charAt(0)) >= 0 && word.length() > 2
				&& (want.isEmpty() || !fold(want.getFirst()).startsWith(word.substring(0, 1)))) {
			return word.substring(2);
		}

		return word;
	}

	private Status compare(String expected, String heard) {
		String e = fold(expected);
		String h = fold(heard);

		boolean sameLetters = !rules.accentsMatter() && WordRules.stripAccents(e).equals(WordRules.stripAccents(h));

		if (e.equals(h) || sameLetters || rules.soundKey(e).equals(rules.soundKey(h))) {
			return Status.MATCH;
		}

		return Status.WRONG;
	}

	/** Lowercase, without apostrophes ("D'or" -> "dor"). */
	private String fold(String word) {
		return language.lowercase(word).replace("'", "").replace("’", "");
	}
}
