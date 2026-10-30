package io.github.dylantopic.langmod.core.lang;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.github.dylantopic.langmod.core.ItemDictionary;
import io.github.dylantopic.langmod.core.MatchMode;
import io.github.dylantopic.langmod.core.Matcher;
import io.github.dylantopic.langmod.core.Pinyin;
import io.github.dylantopic.langmod.core.PinyinEntry;
import io.github.dylantopic.langmod.core.text.CharReadings;
import io.github.dylantopic.langmod.core.text.TextNormalizer;

/**
 * Mandarin: names with pinyin from tools/generate_pinyin.py, transcriptions converted to
 * Simplified characters and compared by pinyin and tone (see {@link Matcher}).
 */
public final class MandarinPack implements LanguagePack {
	/**
	 * Phrases Whisper is known to invent when it gets silence or noise (subtitle credits and the like).
	 * A transcription containing one of them, but not the expected word, is treated as "nothing heard".
	 */
	private static final List<String> NOISE_MARKERS = List.of("谢谢观看", "感谢观看", "多谢观看", "谢谢收看", "谢谢大家",
			"点赞", "订阅", "字幕", "明镜", "点点栏目", "独播剧场");

	private final ItemDictionary dictionary;
	private final TextNormalizer normalizer;
	private final CharReadings readings;

	public MandarinPack(ItemDictionary dictionary, TextNormalizer normalizer, CharReadings readings) {
		this.dictionary = dictionary;
		this.normalizer = normalizer;
		this.readings = readings;
	}

	@Override
	public Language language() {
		return Language.MANDARIN;
	}

	@Override
	public Optional<NameEntry> name(String translationKey) {
		return dictionary.get(translationKey).map(entry -> new NameEntry(translationKey, entry.hanzi(), entry.marks()));
	}

	@Override
	public int nameCount() {
		return dictionary.size();
	}

	@Override
	public int overrideCount() {
		return dictionary.overrideCount();
	}

	@Override
	public String normalize(String transcript) {
		return normalizer.normalize(transcript);
	}

	/** Pinyin of a normalized text using each character's most common reading. */
	@Override
	public String pronounce(String normalized) {
		List<String> parts = new ArrayList<>();

		normalized.codePoints().forEach(cp -> {
			List<Pinyin.Syllable> options = readings.of(cp);
			parts.add(options.isEmpty() ? Character.toString(cp) : options.getFirst().marked());
		});

		return String.join(" ", parts);
	}

	@Override
	public boolean isNoise(String normalized, NameEntry expected) {
		if (normalized.contains(expected.text())) {
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
		PinyinEntry entry = PinyinEntry.of(expected.key(), expected.text(), Pinyin.parseAll(expected.pronunciation()));
		return CheckResult.of(Matcher.match(entry, normalized, readings, mode, longName));
	}
}
