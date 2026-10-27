package io.github.dylantopic.langmod.core.text;

/**
 * Cleans a transcription before matching: Traditional -> Simplified, full-width -> half-width,
 * digits -> Chinese numerals, and punctuation and whitespace removed.
 */
public final class TextNormalizer {
	private static final String DIGITS = "零一二三四五六七八九";

	private final ChineseConverter converter;

	public TextNormalizer(ChineseConverter converter) {
		this.converter = converter;
	}

	public ChineseConverter converter() {
		return converter;
	}

	/** Simplified characters (and any Latin letters) with everything else removed. */
	public String normalize(String raw) {
		String simplified = converter.toSimplified(raw);
		StringBuilder out = new StringBuilder(simplified.length());

		simplified.codePoints().forEach(cp -> {
			// Full-width ASCII (Ａ, １, ！) -> regular ASCII.
			if (cp >= 0xFF01 && cp <= 0xFF5E) {
				cp -= 0xFEE0;
			}

			if (cp >= '0' && cp <= '9') {
				out.append(DIGITS.charAt(cp - '0'));
			} else if (isCjk(cp)) {
				out.appendCodePoint(cp);
			} else if ((cp >= 'a' && cp <= 'z') || (cp >= 'A' && cp <= 'Z')) {
				out.appendCodePoint(Character.toLowerCase(cp));
			}
		});

		return out.toString();
	}

	public static boolean isCjk(int cp) {
		return (cp >= 0x4E00 && cp <= 0x9FFF) || (cp >= 0x3400 && cp <= 0x4DBF) || (cp >= 0xF900 && cp <= 0xFAFF)
				|| (cp >= 0x20000 && cp <= 0x3134F);
	}
}
