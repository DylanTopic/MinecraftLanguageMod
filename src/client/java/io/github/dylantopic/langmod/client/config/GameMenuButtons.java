package io.github.dylantopic.langmod.client.config;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;

/**
 * Adds "Items" and "Practice Language" to the game menu (Esc): a column right of the vanilla
 * buttons, level with Statistics and Report Bugs, or a row below them if the screen is too narrow.
 */
public final class GameMenuButtons {
	public static final String ITEMS = "langmod.menu.items";
	public static final String LANGUAGE = "langmod.menu.language";
	private static final int WIDTH = 98;
	private static final int HEIGHT = 20;
	private static final int GAP = 8;
	private static final int ROW = 24;

	private GameMenuButtons() {
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((minecraft, screen, width, height) -> {
			if (screen instanceof PauseScreen) {
				add(minecraft, screen, width);
			}
		});
	}

	private static void add(Minecraft minecraft, Screen screen, int screenWidth) {
		List<AbstractWidget> widgets = Screens.getWidgets(screen);
		AbstractWidget back = widgets.stream().filter(widget -> hasKey(widget, "menu.returnToGame")).findFirst().orElse(null);

		if (back == null) {
			return; // paused without a menu (F3+Esc)
		}

		int x = back.getX() + back.getWidth() + GAP;
		int itemsX = x;
		int languageX = x;
		int itemsY = back.getY() + ROW;
		int languageY = back.getY() + 2 * ROW;
		int width = WIDTH;

		if (x + WIDTH > screenWidth - 4) {
			int bottom = widgets.stream().filter(widget -> widget.visible).mapToInt(widget -> widget.getY() + widget.getHeight())
					.max().orElse(back.getY() + HEIGHT);
			width = (back.getWidth() - GAP) / 2;
			itemsX = back.getX();
			languageX = back.getX() + back.getWidth() - width;
			itemsY = bottom + 4;
			languageY = bottom + 4;
		}

		widgets.add(Button.builder(Component.translatable(ITEMS), button -> minecraft.setScreen(new ItemsScreen(screen)))
				.pos(itemsX, itemsY).size(width, HEIGHT).build());
		widgets.add(Button.builder(Component.translatable(LANGUAGE), button -> minecraft.setScreen(new LanguageScreen(screen)))
				.pos(languageX, languageY).size(width, HEIGHT).build());
	}

	private static boolean hasKey(AbstractWidget widget, String key) {
		return widget.getMessage().getContents() instanceof TranslatableContents contents && contents.getKey().equals(key);
	}
}
