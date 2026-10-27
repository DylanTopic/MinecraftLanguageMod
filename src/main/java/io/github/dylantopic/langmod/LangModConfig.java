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
import java.util.function.Consumer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import net.fabricmc.loader.api.FabricLoader;

import io.github.dylantopic.langmod.core.MatchMode;

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

	// --- Challenge rules (server) ---------------------------------------------------------
	/** Wrong answers allowed before the item disappears. */
	public int maxAttempts = 3;
	/** Seconds to answer. The countdown pauses while you are speaking and while Whisper is busy. */
	public int timerSeconds = 20;
	/** strict, toneless or lenient. See README. */
	public String matchMode = "strict";
	/** Names with more characters than this are "long". */
	public int maxNameLength = 4;
	/** What to do with long names: lenient (one wrong syllable allowed) or skip (never challenged). */
	public String longNameMode = "lenient";

	// --- Speech recognition (client) -------------------------------------------------------
	/** jni (whisper.cpp inside the game, CPU) or server (a separate whisper-server process, can use the GPU). */
	public String speechEngine = "jni";
	/** Whisper model: tiny, base, small, medium, ... Loads config/langmod/models/ggml-<modelSize>.bin. */
	public String modelSize = "small";
	public boolean useGpu = true;
	/** CPU threads for Whisper; 0 = automatic (leaves cores free for Minecraft). */
	public int whisperThreads = 0;
	/** Pass the expected word to Whisper as its initial prompt. Makes it "hear" the right word more easily. */
	public boolean promptBiasing = false;
	/**
	 * How much audio Whisper encodes per answer: 0 = its full 30 s window (slowest), -1 = just the
	 * clip plus a margin (fastest, less accurate), or a number of 20 ms frames (512 = 10 s).
	 */
	public int audioContext = 0;
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

	public boolean skipLongNames() {
		return "skip".equals(longNameMode);
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
		maxNameLength = clamp(maxNameLength, 1, 32, "maxNameLength");
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

		longNameMode = oneOf(longNameMode, "longNameMode", "lenient", "lenient", "skip");
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
