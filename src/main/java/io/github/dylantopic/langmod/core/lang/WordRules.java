package io.github.dylantopic.langmod.core.lang;

import java.text.Normalizer;
import java.util.List;

/**
 * What differs between Spanish and French when names are compared word by word: the articles a
 * player may put in front of a name, the text Whisper invents for silence, and a rough
 * "sounds like" key, so that words spelled differently but pronounced the same are accepted
 * (Whisper writes words, and picks one spelling when several sound alike).
 */
public enum WordRules {
	SPANISH(List.of("el", "la", "los", "las", "lo", "un", "una", "unos", "unas"),
			List.of("amara", "subtítulos", "suscríbete", "gracias por ver")) {
		@Override
		public String soundKey(String word) {
			// Latin American Spanish: b/v, s/z/soft c and ll/y sound the same; h is silent.
			String s = stripAccents(word.replace("ñ", "ny"));
			s = s.replace("ch", "C").replace("ll", "y").replace("qu", "k");
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
	};

	private final List<String> articles;
	private final List<String> noiseMarkers;

	WordRules(List<String> articles, List<String> noiseMarkers) {
		this.articles = articles;
		this.noiseMarkers = noiseMarkers;
	}

	/** A rough phonetic key of a lowercase word without apostrophes. Same key = sounds the same. */
	public abstract String soundKey(String word);

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
