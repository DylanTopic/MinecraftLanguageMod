package io.github.dylantopic.langmod.client.config;

import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import io.github.dylantopic.langmod.LangModConfig;
import io.github.dylantopic.langmod.client.LangModClient;
import io.github.dylantopic.langmod.core.lang.Language;

/**
 * The menu for choosing which language to learn (from {@code /langmod language}, a key, or the
 * settings screen). Each language keeps its own progress; switching gives back the item of a
 * challenge in progress. A checkbox above Done turns the pronunciation under names on or off.
 */
public class LanguageScreen extends Screen {
	/** Translation key of the pronunciation checkbox's label (the game test clicks it by this). */
	public static final String SHOW_PRONUNCIATION = "langmod.language.showPronunciation";
	private static final int BUTTON_WIDTH = 240;
	private static final int GAP = 8;
	private static final int ROW = 24;
	private static final int HINT_WIDTH = 300;
	private static final int HINT_Y = 30;
	private final Screen parent;
	private List<FormattedCharSequence> hint = List.of();

	/** @param parent screen to return to, or null to go back to the game */
	public LanguageScreen(Screen parent) {
		super(Component.translatable("langmod.language.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		Language current = LangModConfig.get().languageEnum();
		hint = this.font.split(Component.translatable("langmod.language.hint"), Math.min(HINT_WIDTH, this.width - 40));
		Language[] languages = Language.values();
		// As many columns (filled top to bottom) as it takes to stay clear of the checkbox and Done.
		int below = HINT_Y + hint.size() * lineHeight() + 8;
		int bottom = this.height - 32 - ROW;
		int rowsThatFit = Math.max(1, (bottom - below - 20) / ROW + 1);
		int columns = Math.max(1, (languages.length + rowsThatFit - 1) / rowsThatFit);
		int rows = (languages.length + columns - 1) / columns;
		int buttonWidth = Math.min(BUTTON_WIDTH, (this.width - 40 - (columns - 1) * GAP) / columns);
		int left = (this.width - columns * buttonWidth - (columns - 1) * GAP) / 2;
		int top = Math.max(below, Math.min(this.height / 2 - rows * ROW / 2, bottom - (rows - 1) * ROW - 20));

		for (int i = 0; i < languages.length; i++) {
			Language language = languages[i];
			int x = left + (i / rows) * (buttonWidth + GAP);
			int y = top + (i % rows) * ROW;
			this.addRenderableWidget(Button.builder(label(language, language == current, buttonWidth), button -> choose(language))
					.pos(x, y).size(buttonWidth, 20).build());
		}

		Checkbox pronunciation = Checkbox.builder(Component.translatable(SHOW_PRONUNCIATION), this.font)
				.selected(LangModConfig.get().showPronunciation)
				.onValueChange((checkbox, value) -> LangModConfig.update(config -> config.showPronunciation = value))
				.tooltip(Tooltip.create(Component.translatable(SHOW_PRONUNCIATION + ".tooltip")))
				.build();
		pronunciation.setPosition((this.width - pronunciation.getWidth()) / 2, this.height - 28 - ROW);
		this.addRenderableWidget(pronunciation);

		this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
				.pos(this.width / 2 - 100, this.height - 28).size(200, 20).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		graphics.centeredText(this.font, this.title, this.width / 2, 15, 0xFFFFFFFF);
		int y = HINT_Y;

		for (FormattedCharSequence line : hint) {
			graphics.text(this.font, line, (this.width - this.font.width(line)) / 2, y, 0xFFA0A0A8);
			y += lineHeight();
		}
	}

	@Override
	public void onClose() {
		this.minecraft.setScreen(parent);
	}

	/** "✔ Русский - Russian", or just the English name when both do not fit (or say the same). */
	private Component label(Language language, boolean current, int width) {
		String mark = current ? "✔ " : "";
		String both = mark + language.nativeName() + "  -  " + language.englishName();
		boolean same = language.englishName().startsWith(language.nativeName());
		return Component.literal(!same && this.font.width(both) <= width - 8 ? both : mark + language.englishName());
	}

	private int lineHeight() {
		return this.font.lineHeight + 1;
	}

	private void choose(Language language) {
		LangModClient.selectLanguage(language);
		onClose();
	}
}
