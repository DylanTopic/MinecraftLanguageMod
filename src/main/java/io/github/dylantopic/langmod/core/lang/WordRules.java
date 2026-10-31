package io.github.dylantopic.langmod.core.lang;

import java.text.Normalizer;
import java.util.List;

/**
 * What differs between the languages whose names are compared word by word: the articles a
 * player may put in front of a name, the text Whisper invents for silence, and a rough
 * "sounds like" key, so that words spelled differently but pronounced the same are accepted
 * (Whisper writes words, and picks one spelling when several sound alike).
 */
public enum WordRules {
	SPANISH(List.of("el", "la", "los", "las", "lo", "un", "una", "unos", "unas"),
			List.of("amara", "subtítulos", "suscríbete", "gracias por ver")) {
		@Override
		public String soundKey(String word) {
			// Latin American Spanish: b/v, s/z/soft c and ll/y sound the same; h is silent. The soft d
			// between vowels (vidrio) is the English "th", which Whisper sometimes writes ("bithrio").
			String s = stripAccents(word.replace("ñ", "ny"));
			s = s.replace("ch", "C").replace("th", "d").replace("ll", "y").replace("qu", "k");
			s = s.replaceAll("gu(?=[ei])", "G").replaceAll("g(?=[ei])", "j").replace("G", "g");
			s = s.replaceAll("c(?=[ei])", "s").replace("z", "s").replace("c", "k");
			s = s.replace("h", "").replace("v", "b").replace("w", "u").replaceAll("y$", "i").replace("C", "ch");
			return s.replaceAll("([^r])\\1", "$1");
		}
	},
	FRENCH(List.of("le", "la", "les", "un", "une", "des", "du", "de"),
			List.of("amara", "sous-titres", "sous titres", "abonnez-vous", "merci d'avoir regardé")) {
		@Override
		public String soundKey(String word) {
			// é/è/ê sound alike and are kept apart from the silent e ("E"); silent endings are dropped,
			// so verre/vert/vers or doré/dorée/dorés get the same key, while vert/verte do not.
			String s = word.replace("œ", "e").replace("æ", "e").replaceAll("[éèêë]", "E").replaceAll("[àâä]", "a")
					.replaceAll("[îï]", "i").replaceAll("[ôö]", "o").replaceAll("[ùûü]", "u").replace("ç", "s");
			// An n after a vowel and not before another vowel makes the vowel nasal ("N"): pain, diamant,
			// unlike laine. So does an m before a consonant (pompe, champ); a final m is left alone, also
			// before a plural s, as in totem, golem or alliums (and Whisper writes "pom" for pomme).
			s = s.replaceAll("(?<=[aeiouyE])(?:n(?![aeiouyEnmh])|m(?=[^aeiouyEnmh])(?![sx]$))", "N");

			for (int k = 0; k < 2 && s.length() > 2 && "sxztdp".indexOf(s.charAt(s.length() - 1)) >= 0; k++) {
				s = s.substring(0, s.length() - 1);
			}

			if (s.length() > 2 && s.endsWith("e")) {
				s = s.substring(0, s.length() - 1);
			}

			s = s.replace("eau", "o").replace("au", "o").replace("ai", "E").replace("ei", "E").replace("ph", "f").replace("qu", "k").replace("ck", "k");
			s = s.replaceAll("gu(?=[eiE])", "G").replaceAll("g(?=[eiEy])", "j").replace("G", "g");
			s = s.replaceAll("c(?=[eiEy])", "s").replace("c", "k").replace("h", "").replace("y", "i");
			return s.replaceAll("(.)\\1", "$1");
		}
	},
	RUSSIAN(List.of(), List.of("субтитры", "редактор субтитров", "продолжение следует", "спасибо за просмотр", "dimatorzok")) {
		@Override
		public String soundKey(String word) {
			// ё is mostly written е; voiced consonants lose their voice at the end of a word (хлеб = хлеп).
			String s = word.replace('ё', 'е').replace("ться", "ца").replace("тся", "ца");
			s = s.replaceAll("б$", "п").replaceAll("в$", "ф").replaceAll("г$", "к").replaceAll("д$", "т")
					.replaceAll("ж$", "ш").replaceAll("з$", "с");
			return s.replaceAll("(.)\\1+", "$1");
		}

		@Override
		public String romanize(String text) {
			return RussianRomanizer.romanize(text);
		}
	},
	DUTCH(List.of("de", "het", "een", "'t"), List.of("ondertiteling", "ondertiteld door", "bedankt voor het kijken", "amara")) {
		@Override
		public String soundKey(String word) {
			// ij/ei and ou/au sound the same, a final d is a t, g and ch are both the throat sound,
			// and a word-initial v/z is usually said f/s in the Netherlands.
			String s = stripAccents(word).replace("ij", "Y").replace("ei", "Y").replace("ou", "O").replace("au", "O");
			s = s.replace("dt", "t").replaceAll("d$", "t").replace("ch", "G").replace("g", "G").replace("ph", "f");
			s = s.replaceAll("c(?=[eiY])", "s").replace("c", "k").replaceAll("^v", "f").replaceAll("^z", "s");
			return s.replaceAll("(.)\\1+", "$1");
		}
	},
	FILIPINO(List.of("ang", "mga", "ng", "isang", "si"), List.of("salamat sa panonood", "mag-subscribe", "mag subscribe")) {
		@Override
		public String soundKey(String word) {
			// e/i and o/u are not told apart in many Tagalog words, "diya" is said "ja", and letters
			// from Spanish and English loans are said the Tagalog way (c = k, f = p, v = b, z = s).
			String s = stripAccents(word.replace("ñ", "ny")).replace("diya", "ja").replaceAll("^dy", "j");
			s = s.replace("qu", "k").replace("c", "k").replace("f", "p").replace("v", "b").replace("z", "s");
			s = s.replace('e', 'i').replace('o', 'u');
			return s.replaceAll("(.)\\1+", "$1");
		}
	},
	BOSNIAN(List.of(), List.of("titlovi", "prevod", "hvala na pažnji", "hvala što ste gledali", "pretplatite se")) {
		@Override
		public String soundKey(String word) {
			// Many speakers say č and ć, and dž and đ, alike; t drops out between n and s (dijamantski).
			String s = word.replace("dž", "J").replace("đ", "J").replace("č", "C").replace("ć", "C");
			s = stripAccents(s).replace("ntsk", "nsk");
			return s.replaceAll("(.)\\1+", "$1");
		}
	},
	PORTUGUESE(List.of("o", "a", "os", "as", "um", "uma", "uns", "umas"),
			List.of("legendas pela comunidade", "amara", "obrigado por assistir", "obrigada por assistir", "inscreva-se")) {
		@Override
		public String soundKey(String word) {
			// Brazilian Portuguese: ç/ss/s/z alike, final e/o said i/u, nasal vowels kept apart from
			// plain ones (maçã is not massa), and rr/r written either way.
			String s = word.replace("ão", "AUN").replace("ã", "AN").replace("õ", "ON").replace("ç", "s");
			s = stripAccents(s).replace("ch", "X").replace("lh", "L").replace("nh", "N");
			s = s.replaceAll("qu(?=[ei])", "k").replaceAll("gu(?=[ei])", "G").replaceAll("g(?=[ei])", "j").replace("G", "g");
			s = s.replaceAll("c(?=[ei])", "s").replace("c", "k").replace("z", "s").replaceAll("^h", "");
			s = s.replaceAll("es$", "is").replaceAll("os$", "us").replaceAll("e$", "i").replaceAll("o$", "u");
			return s.replaceAll("(.)\\1+", "$1");
		}
	};

	private final List<String> articles;
	private final List<String> noiseMarkers;

	WordRules(List<String> articles, List<String> noiseMarkers) {
		this.articles = articles;
		this.noiseMarkers = noiseMarkers;
	}

	/** A rough phonetic key of a lowercase word without apostrophes. Same key = sounds the same. */
	public abstract String soundKey(String word);

	/** The rules of a language whose names are compared word by word. */
	public static WordRules of(Language language) {
		return switch (language) {
			case SPANISH -> SPANISH;
			case FRENCH -> FRENCH;
			case RUSSIAN -> RUSSIAN;
			case DUTCH -> DUTCH;
			case FILIPINO -> FILIPINO;
			case BOSNIAN -> BOSNIAN;
			case PORTUGUESE -> PORTUGUESE;
			default -> throw new IllegalArgumentException(language + " is not compared word by word");
		};
	}

	/** The name in Latin letters, for languages written in another script; empty otherwise. */
	public String romanize(String text) {
		return "";
	}

	/** Articles that may precede a name ("la manzana", "une pomme", "de la laine"). */
	public List<String> articles() {
		return articles;
	}

	/** Phrases Whisper is known to invent for silence or noise (in normalized form). */
	public List<String> noiseMarkers() {
		return noiseMarkers;
	}

	static String stripAccents(String text) {
		return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
	}
}
