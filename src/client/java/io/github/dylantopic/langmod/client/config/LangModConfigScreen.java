package io.github.dylantopic.langmod.client.config;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

import com.mojang.serialization.Codec;

import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;

import io.github.dylantopic.langmod.LangModConfig;
import io.github.dylantopic.langmod.client.ChallengeController;
import io.github.dylantopic.langmod.client.LangModClient;
import io.github.dylantopic.langmod.client.audio.MicCapture;
import io.github.dylantopic.langmod.client.speech.SpeechService;
import io.github.dylantopic.langmod.core.lang.Language;
import io.github.dylantopic.langmod.core.speech.WhisperModels;

/**
 * Settings screen (Mod Menu's "Configure" button, {@code /langmod config}, or a key). Every change
 * is saved to {@code config/langmod/langmod.json} right away; speech settings take effect when the
 * screen is closed. Options that are rarely changed (VAD timings, whisper-server path, ...) are
 * only in the file.
 */
public class LangModConfigScreen extends OptionsSubScreen {
	private static final String PREFIX = "langmod.config.";
	private static final List<String> MODELS = List.of("tiny", "base", "small", "medium", "large-v3-turbo");
	private static final Map<String, String> PALETTE = palette();

	private final Map<String, OptionInstance<?>> options = new LinkedHashMap<>();
	private final Screen parent;

	/** @param parent screen to return to, or null to go back to the game */
	public LangModConfigScreen(Screen parent) {
		super(parent, Minecraft.getInstance().options, Component.translatable(PREFIX + "title"));
		this.parent = parent;
	}

	@Override
	protected void addOptions() {
		LangModConfig config = LangModConfig.get();
		Language language = config.languageEnum();
		options.clear();

		// Switching language reopens the screen, so the per-language options below show the new language.
		options.put("language", new OptionInstance<Language>(
				PREFIX + "language",
				value -> Tooltip.create(Component.translatable(PREFIX + "language.tooltip")),
				(caption, value) -> Component.literal(value.nativeName()),
				new OptionInstance.Enum<>(List.of(Language.values()), Codec.STRING.xmap(id -> Language.byId(id).orElse(Language.MANDARIN), Language::id)),
				language,
				value -> {
					LangModClient.selectLanguage(value);
					this.minecraft.setScreen(new LangModConfigScreen(parent));
				}));
		toggle("enabled", config.enabled, (c, v) -> c.enabled = v);
		toggle("showEnglish", config.showEnglish, (c, v) -> c.showEnglish = v);
		choice("maxAttempts", List.of(1, 2, 3, 4, 5, 7, 10), config.maxAttempts, Codec.INT,
				v -> Component.literal(Integer.toString(v)), (c, v) -> c.maxAttempts = v);
		choice("timerSeconds", List.of(10, 15, 20, 25, 30, 45, 60, 90, 120), config.timerSeconds, Codec.INT,
				v -> Component.literal(v + " s"), (c, v) -> c.timerSeconds = v);
		choice("matchMode", List.of("strict", "toneless", "lenient"), config.matchMode, Codec.STRING,
				v -> Component.translatable(PREFIX + "matchMode." + v), v -> Component.translatable(PREFIX + "matchMode." + v + ".tooltip"),
				(c, v) -> c.matchMode = v);
		choice("maxNameLength", List.of(1, 2, 3, 4, 5, 6, 8, 10, 12), config.maxNameLength(language), Codec.INT,
				v -> Component.translatable(PREFIX + language.lengthUnit(), v), (c, v) -> c.nameLengthLimits.put(language.id(), v));
		choice("longNameMode", List.of("lenient", "skip"), config.skipLongNames(language) ? "skip" : "lenient", Codec.STRING,
				v -> Component.translatable(PREFIX + "longNameMode." + v), (c, v) -> c.longNameModes.put(language.id(), v));
		toggle("promptBiasing", config.promptBiasing, (c, v) -> c.promptBiasing = v);
		choice("modelSize", MODELS, config.modelSize, Codec.STRING, Component::literal, LangModConfigScreen::modelTooltip,
				(c, v) -> c.modelSize = v);
		choice("speechEngine", List.of("jni", "server"), config.speechEngine, Codec.STRING,
				v -> Component.translatable(PREFIX + "speechEngine." + v), (c, v) -> c.speechEngine = v);
		toggle("useGpu", config.useGpu, (c, v) -> c.useGpu = v);
		choice("audioContext", List.of(0, 768, 512, -1), config.audioContext, Codec.INT, LangModConfigScreen::audioContextName,
				(c, v) -> c.audioContext = v);
		choice("whisperThreads", List.of(0, 1, 2, 3, 4, 6, 8, 12, 16), config.whisperThreads, Codec.INT,
				v -> v == 0 ? Component.translatable(PREFIX + "whisperThreads.auto", WhisperModels.autoThreads()) : Component.literal(Integer.toString(v)),
				(c, v) -> c.whisperThreads = v);
		choice("micDevice", micDevices(), config.micDevice, Codec.STRING,
				v -> v.isEmpty() ? Component.translatable(PREFIX + "micDevice.default") : Component.literal(v), (c, v) -> c.micDevice = v);
		choice("vadThresholdDb", List.of(6.0, 8.0, 10.0, 12.0, 15.0, 18.0, 21.0, 24.0), config.vadThresholdDb, Codec.DOUBLE,
				v -> Component.literal(String.format(Locale.ROOT, "%.0f dB", v)), (c, v) -> c.vadThresholdDb = v);

		for (int tone = 1; language.tonal() && tone <= 5; tone++) {
			String key = Integer.toString(tone);
			choice("toneColor" + tone, List.copyOf(PALETTE.keySet()), config.toneColors.getOrDefault(key, "#FFFFFF").toUpperCase(Locale.ROOT),
					Codec.STRING, LangModConfigScreen::colorName, (c, v) -> c.toneColors.put(key, v));
		}

		choice("hanziScale", List.of(2.0f, 2.5f, 3.0f, 3.5f, 4.0f, 5.0f), config.hanziScale, Codec.FLOAT,
				v -> Component.literal(String.format(Locale.ROOT, "%.1fx", v)), (c, v) -> c.hanziScale = v);
		toggle("debugOverlay", config.debugOverlay, (c, v) -> c.debugOverlay = v);

		if (this.list != null) {
			this.list.addSmall(options.values().toArray(OptionInstance<?>[]::new));
		}
	}

	@Override
	public void removed() {
		super.removed();
		// Reloads the Whisper model only if a speech setting changed, and retries a microphone that failed.
		SpeechService.get().reload();
		ChallengeController.get().clearMicError();
	}

	/** One of the options shown, by config field name (used by the game tests). */
	public OptionInstance<?> option(String name) {
		return options.get(name);
	}

	private void toggle(String name, boolean current, BiConsumer<LangModConfig, Boolean> apply) {
		choice(name, List.of(true, false), current, Codec.BOOL, v -> Component.translatable(v ? "options.on" : "options.off"), apply);
	}

	private <T> void choice(String name, List<T> values, T current, Codec<T> codec, Function<T, Component> label,
			BiConsumer<LangModConfig, T> apply) {
		choice(name, values, current, codec, label, v -> Component.translatable(PREFIX + name + ".tooltip"), apply);
	}

	/**
	 * Adds a button that cycles through {@code values} (plus the current value if the file sets
	 * something else) and writes the choice to the config file.
	 */
	private <T> void choice(String name, List<T> values, T current, Codec<T> codec, Function<T, Component> label,
			Function<T, Component> tooltip, BiConsumer<LangModConfig, T> apply) {
		List<T> all = new ArrayList<>(values);

		if (!all.contains(current)) {
			all.add(current);
		}

		options.put(name, new OptionInstance<T>(
				PREFIX + name,
				value -> Tooltip.create(tooltip.apply(value)),
				(caption, value) -> label.apply(value),
				new OptionInstance.Enum<>(all, codec),
				current,
				value -> LangModConfig.update(config -> apply.accept(config, value))));
	}

	private static Component audioContextName(int frames) {
		return switch (frames) {
			case 0 -> Component.translatable(PREFIX + "audioContext.full");
			case -1 -> Component.translatable(PREFIX + "audioContext.fit");
			default -> Component.translatable(PREFIX + "audioContext.seconds", frames / 50);
		};
	}

	/** "Red", "Blue", ... (or the hex code) drawn in that colour. */
	private static Component colorName(String hex) {
		return Component.literal(PALETTE.getOrDefault(hex, hex)).withStyle(style -> style.withColor(Integer.parseInt(hex.substring(1), 16)));
	}

	private static Component modelTooltip(String size) {
		boolean present = Files.isRegularFile(WhisperModels.modelFile(SpeechService.modelsDirectory(), size));
		return Component.translatable(PREFIX + "modelSize.tooltip", WhisperModels.fileName(size),
				Component.translatable(PREFIX + (present ? "modelSize.found" : "modelSize.missing"),
						SpeechService.modelsDirectory().toAbsolutePath().toString(), WhisperModels.downloadUrl(size)));
	}

	private static List<String> micDevices() {
		List<String> devices = new ArrayList<>();
		devices.add("");

		try {
			devices.addAll(MicCapture.deviceNames());
		} catch (RuntimeException e) {
			// No sound system: only the default entry.
		}

		return devices;
	}

	private static Map<String, String> palette() {
		Map<String, String> colors = new LinkedHashMap<>();
		colors.put("#E53935", "Red");
		colors.put("#FB8C00", "Orange");
		colors.put("#FDD835", "Yellow");
		colors.put("#43A047", "Green");
		colors.put("#00897B", "Teal");
		colors.put("#1E88E5", "Blue");
		colors.put("#8E24AA", "Purple");
		colors.put("#D81B60", "Pink");
		colors.put("#6D4C41", "Brown");
		colors.put("#9E9E9E", "Grey");
		colors.put("#FFFFFF", "White");
		return colors;
	}
}
