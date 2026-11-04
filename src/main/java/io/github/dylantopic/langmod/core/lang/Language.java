package io.github.dylantopic.langmod.core.lang;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * A language the mod can teach. Item names come from Minecraft's own translation for the locale
 * {@link #id()}; Whisper is told to expect {@link #whisperCode()}.
 */
public enum Language {
	MANDARIN("zh_cn", "zh", "Mandarin Chinese", "中文", 4, "lenient", "我的世界物品：", false),
	SPANISH("es_mx", "es", "Spanish (Mexico)", "Español (México)", 3, "skip", "Objetos de Minecraft:", true),
	FRENCH("fr_ca", "fr", "French (Canada)", "Français (Canada)", 3, "skip", "Objets de Minecraft :", true),
	KOREAN("ko_kr", "ko", "Korean", "한국어", 2, "skip", "마인크래프트 아이템:", true),
	RUSSIAN("ru_ru", "ru", "Russian", "Русский", 3, "skip", "Предметы Minecraft:", true),
	DUTCH("nl_nl", "nl", "Dutch", "Nederlands", 2, "skip", "Minecraft-voorwerpen:", true),
	LATIN("la_la", "la", "Latin", "Latina", 3, "skip", "Res Minecraft:", true),
	FILIPINO("fil_ph", "tl", "Tagalog (Filipino)", "Tagalog", 3, "skip", "Mga item sa Minecraft:", true),
	JAPANESE("ja_jp", "ja", "Japanese", "日本語", 12, "skip", "マインクラフトのアイテム：", true),
	BOSNIAN("bs_ba", "bs", "Bosnian", "Bosanski", 3, "skip", "Predmeti iz Minecrafta:", true),
	PORTUGUESE("pt_br", "pt", "Portuguese (Brazil)", "Português (Brasil)", 3, "skip", "Itens do Minecraft:", true),
	VIETNAMESE("vi_vn", "vi", "Vietnamese", "Tiếng Việt", 4, "skip", "Vật phẩm Minecraft:", true),
	GERMAN("de_de", "de", "German", "Deutsch", 2, "skip", "Minecraft-Gegenstände:", true),
	ENGLISH("en_us", "en", "English", "English", 3, "skip", "Minecraft items:", true);

	private final String id;
	private final String whisperCode;
	private final String englishName;
	private final String nativeName;
	private final int defaultMaxLength;
	private final String defaultLongNameMode;
	private final String defaultPrompt;
	private final boolean defaultPromptBiasing;

	Language(String id, String whisperCode, String englishName, String nativeName, int defaultMaxLength, String defaultLongNameMode,
			String defaultPrompt, boolean defaultPromptBiasing) {
		this.id = id;
		this.whisperCode = whisperCode;
		this.englishName = englishName;
		this.nativeName = nativeName;
		this.defaultMaxLength = defaultMaxLength;
		this.defaultLongNameMode = defaultLongNameMode;
		this.defaultPrompt = defaultPrompt;
		this.defaultPromptBiasing = defaultPromptBiasing;
	}

	public static Optional<Language> byId(String id) {
		for (Language language : values()) {
			if (language.id.equals(id)) {
				return Optional.of(language);
			}
		}

		return Optional.empty();
	}

	/** Minecraft locale code, e.g. {@code es_mx}; also the key for per-language settings and progress. */
	public String id() {
		return id;
	}

	/** Language code Whisper is forced to, e.g. {@code es}. */
	public String whisperCode() {
		return whisperCode;
	}

	public String englishName() {
		return englishName;
	}

	public String nativeName() {
		return nativeName;
	}

	/** Names longer than this ({@link #length}) are "long" unless the config says otherwise. */
	public int defaultMaxLength() {
		return defaultMaxLength;
	}

	/** {@code lenient} or {@code skip}. */
	public String defaultLongNameMode() {
		return defaultLongNameMode;
	}

	/** Generic initial prompt for Whisper (never contains the answer). */
	public String defaultPrompt() {
		return defaultPrompt;
	}

	/**
	 * Whether Whisper gets the expected name as its prompt unless the config says otherwise. On where
	 * Whisper missed many correct answers without it in tests (Spanish, French, Korean); off for
	 * Mandarin, where it let a wrong tone pass.
	 */
	public boolean defaultPromptBiasing() {
		return defaultPromptBiasing;
	}

	/** Mandarin has tones (checked, and shown in colour); the others do not. */
	public boolean tonal() {
		return this == MANDARIN;
	}

	/** Mandarin and Japanese are written without spaces, so their names are measured in characters. */
	public boolean countsCharacters() {
		return this == MANDARIN || this == JAPANESE;
	}

	/** What {@link #length} counts, for messages: "characters", "syllables" (Vietnamese writes a space after each) or "words". */
	public String lengthUnit() {
		return countsCharacters() ? "characters" : this == VIETNAMESE ? "syllables" : "words";
	}

	/** Length of a name for the long-name rule: characters for Mandarin and Japanese, words otherwise. */
	public int length(String name) {
		if (countsCharacters()) {
			return name.codePointCount(0, name.length());
		}

		String trimmed = name.trim();
		return trimmed.isEmpty() ? 0 : trimmed.split("\\s+").length;
	}

	/**
	 * The parts of a name that are compared one by one and marked right or wrong on screen:
	 * characters for Mandarin and Japanese, syllables for Korean (Latin letters and digits stay
	 * together), words for the others (hyphens separate words too).
	 */
	public List<String> units(String text) {
		List<String> units = new ArrayList<>();

		switch (this) {
			case MANDARIN, JAPANESE -> text.codePoints().filter(cp -> !Character.isWhitespace(cp)).forEach(cp -> units.add(Character.toString(cp)));
			case KOREAN -> {
				StringBuilder run = new StringBuilder();

				for (int cp : text.codePoints().toArray()) {
					if (Hangul.isSyllable(cp)) {
						flush(run, units);
						units.add(Character.toString(cp));
					} else if (Character.isLetterOrDigit(cp)) {
						run.appendCodePoint(cp);
					} else {
						flush(run, units);
					}
				}

				flush(run, units);
			}
			default -> {
				for (String word : text.trim().split("[\\s\\-]+")) {
					if (!word.isEmpty()) {
						units.add(word);
					}
				}
			}
		}

		return units;
	}

	public String lowercase(String text) {
		return text.toLowerCase(Locale.forLanguageTag(id.replace('_', '-')));
	}

	private static void flush(StringBuilder run, List<String> units) {
		if (!run.isEmpty()) {
			units.add(run.toString());
			run.setLength(0);
		}
	}
}
