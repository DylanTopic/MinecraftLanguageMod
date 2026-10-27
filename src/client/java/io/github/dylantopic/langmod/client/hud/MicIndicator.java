package io.github.dylantopic.langmod.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Util;

import io.github.dylantopic.langmod.client.MicTest;
import io.github.dylantopic.langmod.client.audio.MicCapture;

/** "Microphone on" indicator with a live level meter (also handy for tuning the VAD threshold). */
public final class MicIndicator {
	private static final int RED = 0xFFE53935;

	private MicIndicator() {
	}

	/** A pulsing red dot; grey when the microphone is closed. */
	public static void drawDot(GuiGraphicsExtractor graphics, int x, int y, boolean on, boolean speaking) {
		int color = 0xFF606068;

		if (on) {
			boolean bright = speaking || (Util.getMillis() / 500) % 2 == 0;
			color = bright ? RED : 0xFF8E1B19;
		}

		graphics.fill(x + 2, y, x + 6, y + 8, color);
		graphics.fill(x + 1, y + 1, x + 7, y + 7, color);
		graphics.fill(x, y + 2, x + 8, y + 6, color);
	}

	/** Level meter from -70 dB to -10 dB with a white tick at the speech threshold. */
	public static void drawMeter(GuiGraphicsExtractor graphics, int x, int y, int width, MicCapture mic) {
		graphics.fill(x, y, x + width, y + 3, 0xFF303038);
		int level = toPixels(mic.levelDb(), width);
		graphics.fill(x, y, x + level, y + 3, mic.inSpeech() ? 0xFF43A047 : 0xFF7C8A99);
		int threshold = toPixels(mic.thresholdDb(), width);
		graphics.fill(x + threshold, y - 1, x + threshold + 1, y + 4, 0xFFFFFFFF);
	}

	/** Small panel shown while /langmod test is listening. */
	public static void drawMicTest(GuiGraphicsExtractor graphics, MicTest test) {
		Font font = Minecraft.getInstance().font;
		MicCapture mic = test.mic();
		String text = test.checking() ? "Checking..." : "Mic on (/langmod test): say something";
		int width = font.width(text) + 26;
		int x = (graphics.guiWidth() - width) / 2;
		int y = 6;
		graphics.fill(x, y, x + width, y + 22, 0xD0101018);
		drawDot(graphics, x + 6, y + 5, !test.checking(), mic != null && mic.inSpeech());
		graphics.text(font, text, x + 18, y + 5, 0xFFFFFFFF, true);

		if (mic != null && !test.checking()) {
			drawMeter(graphics, x + 6, y + 16, width - 12, mic);
		}
	}

	private static int toPixels(float db, int width) {
		float fraction = (db + 70f) / 60f;
		return Math.round(Math.max(0, Math.min(1, fraction)) * width);
	}
}
