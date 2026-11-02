package io.github.dylantopic.langmod;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import net.fabricmc.loader.api.FabricLoader;

import io.github.dylantopic.langmod.core.MatchMode;
import io.github.dylantopic.langmod.core.lang.Language;

/**
 * Settings stored in {@code config/langmod/langmod.json}. The same file is read by the logical
 * server (challenge rules) and the client (speech, microphone and display options).
 *
 * <p>Instances are treated as immutable snapshots: {@link #get()} returns the current one and
 * {@link #update(Consumer)} swaps in a modified copy, so background threads never see a half
 * edited configuration.
 */
public final class LangModConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static volatile LangModConfig current = new LangModConfig();

	// --- General --------------------------------------------------------------------------
	/** Master switch. When off, pickups behave exactly like vanilla. */
	public boolean enabled = true;
	/** The language being learned: zh_cn, es_mx, fr_ca or ko_kr. Each has its own progress. */
	public String language = Language.MANDARIN.id();

	// --- Challenge rules (server) ---------------------------------------------------------
	/** Wrong answers allowed before the item disappears. */
	public int maxAttempts = 3;
	/** Seconds to answer. The countdown pauses while you are speaking and while Whisper is busy. */
	public int timerSeconds = 20;
	/** strict, toneless or lenient. See README. */
	public String matchMode = "strict";
	/** Per language: names longer than this (characters in Mandarin, words otherwise) are "long". */
	public Map<String, Integer> nameLengthLimits = defaultNameLengthLimits();
	/** Per language: what to do with long names: lenient (one wrong unit allowed) or skip (never challenged). */
	public Map<String, String> longNameModes = defaultLongNameModes();
	/** Replaced by {@link #nameLengthLimits} / {@link #longNameModes}; read from old files, then moved there. */
	public Integer maxNameLength;
	public String longNameMode;
	/**
	 * Per language: ids of items turned off in the Items screen (e.g. {@code minecraft:dirt}). They are
	 * picked up normally and never challenged. Every item is on by default.
	 */
	public Map<String, Set<String>> disabledItems = defaultDisabledItems();

	// --- Speech recognition (client) -------------------------------------------------------
	/** jni (whisper.cpp inside the game, CPU) or server (a separate whisper-server process, can use the GPU). */
	public String speechEngine = "jni";
	/** Whisper model: tiny, base, small, medium, ... Loads config/langmod/models/ggml-<modelSize>.bin. */
	public String modelSize = "small";
	public boolean useGpu = true;
	/** CPU threads for Whisper; 0 = automatic (leaves cores free for Minecraft). */
	public int whisperThreads = 0;
	/** Per language: pass the expected name to Whisper as its initial prompt, so it "hears" the right word more easily. */
	public Map<String, Boolean> promptBiasingByLanguage = defaultPromptBiasing();
	/** Replaced by {@link #promptBiasingByLanguage} (it was the Mandarin setting); read from old files, then moved there. */
	public Boolean promptBiasing;
	/**
	 * Per language: the initial prompt when prompt biasing is off. It never contains the answer:
	 * "Minecraft items:" in the language, which made Whisper prefer item names over everyday words
	 * that sound alike in Mandarin (it made no difference in the other languages). Empty = no prompt.
	 */
	public Map<String, String> whisperPrompts = defaultWhisperPrompts();
	/** Replaced by {@link #whisperPrompts} (it was the Mandarin prompt); read from old files, then moved there. */
	public String whisperPrompt;
	/**
	 * How much audio Whisper encodes per answer: 0 = its full 30 s window, -1 = just the clip plus a
	 * margin (fastest, much less accurate), or a number of 20 ms frames. 512 (10 s) was as accurate as
	 * the full window in tests and about 4x faster.
	 */
	public int audioContext = 512;
	/** A transcription that takes longer than this is abandoned and does not count as an attempt. */
	public int transcriptionTimeoutSeconds = 10;
	/** Full path to whisper-server(.exe), only used when speechEngine is "server". */
	public String whisperServerPath = "";
	public int whisperServerPort = 8178;

	// --- Microphone (client) ----------------------------------------------------------------
	/** Empty = system default, otherwise (part of) the device name. See /langmod mics. */
	public String micDevice = "";
	/** How far above the background noise (in dB) a sound must be to count as speech. */
	public double vadThresholdDb = 12.0;
	/** Silence that ends a recording. */
	public int vadSilenceMs = 800;
	/** Longest recording. */
	public int vadMaxClipMs = 5000;
	/** Shorter sounds (clicks, bumps) are ignored. */
	public int vadMinSpeechMs = 200;

	// --- Display (client) -------------------------------------------------------------------
	public boolean showEnglish = true;
	/** Say the name out loud (with a voice installed in Windows) after a wrong answer and when an item is lost. */
	public boolean sayNameWhenWrong = true;
	/** Speed of the voice that says names: 1.0 = normal. */
	public double voiceSpeed = 0.9;
	/** Per language: the voice that says names, as (part of) its name; empty = chosen automatically. See /langmod voices. */
	public Map<String, String> voiceNames = defaultVoiceNames();
	/** Colours for tones 1-4 and 5 (neutral), as #RRGGBB. */
	public Map<String, String> toneColors = defaultToneColors();
	public float hanziScale = 3.0f;
	public float pinyinScale = 1.5f;
	public boolean debugOverlay = false;

	public static LangModConfig get() {
		return current;
	}

	public static Path directory() {
		return FabricLoader.getInstance().getConfigDir().resolve(LangMod.MOD_ID);
	}

	public static Path file() {
		return directory().resolve("langmod.json");
	}

	/** Loads the config file, creating it with defaults on first run. */
	public static synchronized LangModConfig load() {
		Path file = file();
		LangModConfig loaded = new LangModConfig();

		if (Files.exists(file)) {
			try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				LangModConfig parsed = GSON.fromJson(reader, LangModConfig.class);

				if (parsed != null) {
					loaded = parsed;
				}
			} catch (IOException | JsonParseException e) {
				LangMod.LOGGER.error("Could not read {}, using defaults: {}", file, e.getMessage());
			}
		}

		loaded.validate();
		current = loaded;
		save(loaded);
		return loaded;
	}

	/** Applies a change to a copy of the current config, saves it and makes it current. */
	public static synchronized LangModConfig update(Consumer<LangModConfig> change) {
		LangModConfig copy = GSON.fromJson(GSON.toJson(current), LangModConfig.class);
		change.accept(copy);
		copy.validate();
		current = copy;
		save(copy);
		return copy;
	}

	/** Replaces the current config without touching the file (used by tests). */
	public static void setForTesting(LangModConfig config) {
		config.validate();
		current = config;
	}

	public LangModConfig copy() {
		return GSON.fromJson(GSON.toJson(this), LangModConfig.class);
	}

	private static void save(LangModConfig config) {
		try {
			Files.createDirectories(file().getParent());

			try (Writer writer = Files.newBufferedWriter(file(), StandardCharsets.UTF_8)) {
				GSON.toJson(config, writer);
			}
		} catch (IOException e) {
			LangMod.LOGGER.error("Could not save {}: {}", file(), e.getMessage());
		}
	}

	public MatchMode matchModeEnum() {
		return MatchMode.fromString(matchMode).orElse(MatchMode.STRICT);
	}

	public Language languageEnum() {
		return Language.byId(language).orElse(Language.MANDARIN);
	}

	public int maxNameLength(Language language) {
		return nameLengthLimits.getOrDefault(language.id(), language.defaultMaxLength());
	}

	public boolean skipLongNames(Language language) {
		return "skip".equals(longNameModes.getOrDefault(language.id(), language.defaultLongNameMode()));
	}

	/** False if the item was turned off for this language in the Items screen. */
	public boolean isItemEnabled(Language language, String itemId) {
		Set<String> disabled = disabledItems.get(language.id());
		return disabled == null || !disabled.contains(itemId);
	}

	public int disabledItemCount(Language language) {
		return disabledItems.getOrDefault(language.id(), Set.of()).size();
	}

	/** Turns an item on or off for a language. Call it on the copy passed to {@link #update}. */
	public void setItemEnabled(Language language, String itemId, boolean enabled) {
		Set<String> disabled = new TreeSet<>(disabledItems.getOrDefault(language.id(), Set.of()));

		if (enabled) {
			disabled.remove(itemId);
		} else {
			disabled.add(itemId);
		}

		disabledItems.put(language.id(), disabled);
	}

	public boolean promptBiasing(Language language) {
		return promptBiasingByLanguage.getOrDefault(language.id(), language.defaultPromptBiasing());
	}

	/** The generic prompt for a language, or null for none. */
	public String whisperPrompt(Language language) {
		String prompt = whisperPrompts.getOrDefault(language.id(), language.defaultPrompt());
		return prompt.isBlank() ? null : prompt;
	}

	/** The voice chosen for a language in the settings, or empty for automatic. */
	public String voiceName(Language language) {
		return voiceNames.getOrDefault(language.id(), "");
	}

	/** Tone colour as 0xAARRGGBB; tone 5 = neutral. */
	public int toneColor(int tone) {
		String value = toneColors.get(Integer.toString(tone));

		try {
			return 0xFF000000 | Integer.parseInt(value.replace("#", ""), 16);
		} catch (RuntimeException e) {
			return 0xFFFFFFFF;
		}
	}

	private void validate() {
		maxAttempts = clamp(maxAttempts, 1, 20, "maxAttempts");
		timerSeconds = clamp(timerSeconds, 3, 600, "timerSeconds");
		migrateOldSettings();

		if (Language.byId(language).isEmpty()) {
			LangMod.LOGGER.warn("Config: unknown language '{}', using {}", language, Language.MANDARIN.id());
			language = Language.MANDARIN.id();
		}

		Map<String, Integer> limits = defaultNameLengthLimits();
		Map<String, String> modes = defaultLongNameModes();
		Map<String, String> prompts = defaultWhisperPrompts();
		Map<String, Boolean> biasing = defaultPromptBiasing();
		Map<String, Set<String>> disabled = defaultDisabledItems();
		Map<String, String> voices = defaultVoiceNames();

		for (Language l : Language.values()) {
			if (voiceNames != null && voiceNames.get(l.id()) != null) {
				voices.put(l.id(), voiceNames.get(l.id()).trim());
			}

			if (nameLengthLimits != null && nameLengthLimits.get(l.id()) != null) {
				limits.put(l.id(), clamp(nameLengthLimits.get(l.id()), 1, 32, "nameLengthLimits." + l.id()));
			}

			if (longNameModes != null && longNameModes.get(l.id()) != null) {
				modes.put(l.id(), oneOf(longNameModes.get(l.id()), "longNameModes." + l.id(), l.defaultLongNameMode(), "lenient", "skip"));
			}

			if (whisperPrompts != null && whisperPrompts.get(l.id()) != null) {
				prompts.put(l.id(), whisperPrompts.get(l.id()));
			}

			if (promptBiasingByLanguage != null && promptBiasingByLanguage.get(l.id()) != null) {
				biasing.put(l.id(), promptBiasingByLanguage.get(l.id()));
			}

			if (disabledItems != null && disabledItems.get(l.id()) != null) {
				for (String id : disabledItems.get(l.id())) {
					if (id != null && !id.isBlank()) {
						disabled.get(l.id()).add(id.trim());
					}
				}
			}
		}

		nameLengthLimits = limits;
		longNameModes = modes;
		whisperPrompts = prompts;
		promptBiasingByLanguage = biasing;
		disabledItems = disabled;
		voiceNames = voices;

		if (!(voiceSpeed >= 0.5 && voiceSpeed <= 3)) {
			LangMod.LOGGER.warn("Config: voiceSpeed {} out of range, using 0.9", voiceSpeed);
			voiceSpeed = 0.9;
		}
		whisperThreads = clamp(whisperThreads, 0, 64, "whisperThreads");
		audioContext = clamp(audioContext, -1, 1500, "audioContext");
		transcriptionTimeoutSeconds = clamp(transcriptionTimeoutSeconds, 1, 120, "transcriptionTimeoutSeconds");
		whisperServerPort = clamp(whisperServerPort, 1, 65535, "whisperServerPort");
		vadSilenceMs = clamp(vadSilenceMs, 100, 5000, "vadSilenceMs");
		vadMaxClipMs = clamp(vadMaxClipMs, 1000, 30000, "vadMaxClipMs");
		vadMinSpeechMs = clamp(vadMinSpeechMs, 0, 2000, "vadMinSpeechMs");

		if (!(vadThresholdDb >= 1 && vadThresholdDb <= 60)) {
			LangMod.LOGGER.warn("Config: vadThresholdDb {} out of range, using 12", vadThresholdDb);
			vadThresholdDb = 12;
		}

		if (!(hanziScale >= 1 && hanziScale <= 8)) {
			hanziScale = 3;
		}

		if (!(pinyinScale >= 0.5f && pinyinScale <= 6)) {
			pinyinScale = 1.5f;
		}

		matchMode = matchMode == null ? "" : matchMode.toLowerCase(Locale.ROOT);

		if (MatchMode.fromString(matchMode).isEmpty()) {
			LangMod.LOGGER.warn("Config: unknown matchMode '{}', using strict", matchMode);
			matchMode = "strict";
		}

		speechEngine = oneOf(speechEngine, "speechEngine", "jni", "jni", "server");

		if (modelSize == null || modelSize.isBlank() || !modelSize.matches("[A-Za-z0-9._-]+")) {
			LangMod.LOGGER.warn("Config: invalid modelSize '{}', using small", modelSize);
			modelSize = "small";
		}

		if (micDevice == null) {
			micDevice = "";
		}

		if (whisperServerPath == null) {
			whisperServerPath = "";
		}


		Map<String, String> colors = defaultToneColors();

		if (toneColors != null) {
			for (Map.Entry<String, String> e : toneColors.entrySet()) {
				if (colors.containsKey(e.getKey()) && e.getValue() != null && e.getValue().matches("#?[0-9A-Fa-f]{6}")) {
					colors.put(e.getKey(), e.getValue().startsWith("#") ? e.getValue() : "#" + e.getValue());
				} else {
					LangMod.LOGGER.warn("Config: ignoring tone colour {}={}", e.getKey(), e.getValue());
				}
			}
		}

		toneColors = colors;
	}

	/** Settings that applied to Mandarin only before other languages were added. */
	private void migrateOldSettings() {
		String mandarin = Language.MANDARIN.id();

		if (maxNameLength != null) {
			nameLengthLimits = new LinkedHashMap<>(nameLengthLimits == null ? Map.of() : nameLengthLimits);
			nameLengthLimits.put(mandarin, maxNameLength);
			maxNameLength = null;
		}

		if (longNameMode != null) {
			longNameModes = new LinkedHashMap<>(longNameModes == null ? Map.of() : longNameModes);
			longNameModes.put(mandarin, longNameMode);
			longNameMode = null;
		}

		if (whisperPrompt != null) {
			whisperPrompts = new LinkedHashMap<>(whisperPrompts == null ? Map.of() : whisperPrompts);
			whisperPrompts.put(mandarin, whisperPrompt);
			whisperPrompt = null;
		}

		if (promptBiasing != null) {
			promptBiasingByLanguage = new LinkedHashMap<>(promptBiasingByLanguage == null ? Map.of() : promptBiasingByLanguage);
			promptBiasingByLanguage.put(mandarin, promptBiasing);
			promptBiasing = null;
		}
	}

	private static Map<String, Integer> defaultNameLengthLimits() {
		Map<String, Integer> limits = new LinkedHashMap<>();

		for (Language language : Language.values()) {
			limits.put(language.id(), language.defaultMaxLength());
		}

		return limits;
	}

	private static Map<String, String> defaultLongNameModes() {
		Map<String, String> modes = new LinkedHashMap<>();

		for (Language language : Language.values()) {
			modes.put(language.id(), language.defaultLongNameMode());
		}

		return modes;
	}

	private static Map<String, String> defaultWhisperPrompts() {
		Map<String, String> prompts = new LinkedHashMap<>();

		for (Language language : Language.values()) {
			prompts.put(language.id(), language.defaultPrompt());
		}

		return prompts;
	}

	private static Map<String, Set<String>> defaultDisabledItems() {
		Map<String, Set<String>> disabled = new LinkedHashMap<>();

		for (Language language : Language.values()) {
			disabled.put(language.id(), new TreeSet<>());
		}

		return disabled;
	}

	private static Map<String, String> defaultVoiceNames() {
		Map<String, String> voices = new LinkedHashMap<>();

		for (Language language : Language.values()) {
			voices.put(language.id(), "");
		}

		return voices;
	}

	private static Map<String, Boolean> defaultPromptBiasing() {
		Map<String, Boolean> biasing = new LinkedHashMap<>();

		for (Language language : Language.values()) {
			biasing.put(language.id(), language.defaultPromptBiasing());
		}

		return biasing;
	}

	private static Map<String, String> defaultToneColors() {
		// Pleco-style colours: 1 red, 2 green, 3 blue, 4 purple, neutral grey.
		Map<String, String> colors = new LinkedHashMap<>();
		colors.put("1", "#E53935");
		colors.put("2", "#43A047");
		colors.put("3", "#1E88E5");
		colors.put("4", "#8E24AA");
		colors.put("5", "#9E9E9E");
		return colors;
	}

	private static int clamp(int value, int min, int max, String name) {
		if (value < min || value > max) {
			int clamped = Math.max(min, Math.min(max, value));
			LangMod.LOGGER.warn("Config: {}={} out of range [{}, {}], using {}", name, value, min, max, clamped);
			return clamped;
		}

		return value;
	}

	private static String oneOf(String value, String name, String fallback, String... allowed) {
		String v = value == null ? "" : value.toLowerCase(Locale.ROOT);

		for (String a : allowed) {
			if (a.equals(v)) {
				return v;
			}
		}

		LangMod.LOGGER.warn("Config: unknown {} '{}', using {}", name, value, fallback);
		return fallback;
	}
}
