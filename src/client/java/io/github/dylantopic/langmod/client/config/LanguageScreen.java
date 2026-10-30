package io.github.dylantopic.langmod.client.config;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import io.github.dylantopic.langmod.LangModConfig;
import io.github.dylantopic.langmod.client.LangModClient;
import io.github.dylantopic.langmod.core.lang.Language;

/**
 * The menu for choosing which language to learn (from {@code /langmod language}, a key, or the
 * settings screen). Each language keeps its own progress; switching gives back the item of a
 * challenge in progress.
 */
public class LanguageScreen extends Screen {
	private static final int BUTTON_WIDTH = 240;
	private final Screen parent;

	/** @param parent screen to return to, or null to go back to the game */
	public LanguageScreen(Screen parent) {
		super(Component.translatable("langmod.language.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		Language current = LangModConfig.get().languageEnum();
		int x = (this.width - BUTTON_WIDTH) / 2;
		int y = Math.max(48, this.height / 2 - Language.values().length * 12);

		for (Language language : Language.values()) {
			Component label = Component.literal((language == current ? "✔ " : "") + language.nativeName() + "  -  " + language.englishName());
			this.addRenderableWidget(Button.builder(label, button -> choose(language)).pos(x, y).size(BUTTON_WIDTH, 20).build());
			y += 24;
		}

		this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
				.pos(this.width / 2 - 100, this.height - 28).size(200, 20).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		graphics.centeredText(this.font, this.title, this.width / 2, 15, 0xFFFFFFFF);
		graphics.centeredText(this.font, Component.translatable("langmod.language.hint"), this.width / 2, 30, 0xFFA0A0A8);
	}

	@Override
	public void onClose() {
		this.minecraft.setScreen(parent);
	}

	private void choose(Language language) {
		LangModClient.selectLanguage(language);
		onClose();
	}
}
