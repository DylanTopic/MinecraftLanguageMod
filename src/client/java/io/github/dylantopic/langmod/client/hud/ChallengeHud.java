package io.github.dylantopic.langmod.client.hud;

import java.util.List;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;

import io.github.dylantopic.langmod.LangModConfig;
import io.github.dylantopic.langmod.client.ClientChallenge;
import io.github.dylantopic.langmod.core.Pinyin;
import io.github.dylantopic.langmod.core.PinyinEntry;
import io.github.dylantopic.langmod.net.ChallengeEndPayload;

/**
 * The challenge popup: item icon, the Chinese name in large characters, tone-coloured pinyin,
 * the English name, microphone state and the countdown.
 */
public final class ChallengeHud {
	private static final int PAD = 6;
	private static final int ICON = 32;
	private static final int BACKGROUND = 0xD0101018;
	private static final int BORDER = 0xFF5A5A78;
	private static final int TEXT = 0xFFFFFFFF;
	private static final int DIM = 0xFFA0A0A8;

	private ChallengeHud() {
	}

	public static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		ClientChallenge challenge = ClientChallenge.get();

		if (challenge.active() != null) {
			drawChallenge(graphics, challenge, challenge.active());
		} else if (challenge.ended() != null) {
			drawResult(graphics, challenge, challenge.ended());
		}
	}

	private static void drawChallenge(GuiGraphicsExtractor graphics, ClientChallenge challenge, ClientChallenge.Active active) {
		Minecraft minecraft = Minecraft.getInstance();
		Font font = minecraft.font;
		LangModConfig config = LangModConfig.get();
		PinyinEntry entry = active.entry();

		float hanziScale = config.hanziScale;
		float pinyinScale = config.pinyinScale;
		String english = config.showEnglish ? Component.translatable(active.translationKey()).getString() : null;

		int hanziWidth = Math.round(font.width(entry.hanzi()) * hanziScale);
		int pinyinWidth = Math.round(pinyinWidth(font, entry.syllables()) * pinyinScale);
		int englishWidth = english == null ? 0 : font.width(english);
		int textWidth = Math.max(hanziWidth, Math.max(pinyinWidth, englishWidth));

		String status = statusLine();
		String timer = (challenge.remainingTicks() + 19) / 20 + "s";
		String attempts = "Attempt " + Math.min(challenge.attemptsUsed() + 1, active.maxAttempts()) + "/" + active.maxAttempts()
				+ (active.longName() ? "  (long name: one slip allowed)" : "")
				+ (challenge.queued() > 0 ? "  +" + challenge.queued() + " queued" : "");
		int footerWidth = Math.max(12 + font.width(status) + 8 + font.width(timer), font.width(attempts));

		int width = Math.max(ICON + PAD + textWidth, Math.max(footerWidth, 170)) + 2 * PAD;
		int hanziHeight = Math.round(font.lineHeight * hanziScale);
		int pinyinHeight = Math.round(font.lineHeight * pinyinScale);
		int textHeight = hanziHeight + 3 + pinyinHeight + (english == null ? 0 : 3 + font.lineHeight);
		int headerHeight = Math.max(ICON, textHeight);
		int height = PAD + headerHeight + 6 + font.lineHeight + 4 + 3 + 4 + font.lineHeight + PAD;

		int x = (graphics.guiWidth() - width) / 2;
		int y = 6;
		graphics.fill(x, y, x + width, y + height, BACKGROUND);
		graphics.outline(x, y, width, height, BORDER);

		// Item icon, drawn at twice the normal size.
		int iconY = y + PAD + (headerHeight - ICON) / 2;
		graphics.pose().pushMatrix();
		graphics.pose().translate(x + PAD, iconY);
		graphics.pose().scale(2.0f);
		graphics.item(active.stack(), 0, 0);
		graphics.pose().popMatrix();

		// Name: characters, pinyin coloured by tone, English.
		int textX = x + PAD + ICON + PAD;
		int textY = y + PAD + (headerHeight - textHeight) / 2;
		drawScaled(graphics, font, entry.hanzi(), textX, textY, hanziScale, TEXT);
		textY += hanziHeight + 3;
		drawPinyin(graphics, font, entry.syllables(), textX, textY, pinyinScale, config);
		textY += pinyinHeight + 3;

		if (english != null) {
			graphics.text(font, english, textX, textY, DIM, true);
		}

		// Microphone indicator, status text and countdown.
		int rowY = y + PAD + headerHeight + 6;
		drawMicIndicator(graphics, x + PAD, rowY);
		graphics.text(font, status, x + PAD + 12, rowY, TEXT, true);
		graphics.text(font, timer, x + width - PAD - font.width(timer), rowY, challenge.timerRunning() ? TEXT : DIM, true);

		// Time bar: green -> red as the time runs out.
		int barY = rowY + font.lineHeight + 4;
		int barWidth = width - 2 * PAD;
		float fraction = active.timeLimitTicks() <= 0 ? 0 : Math.max(0, Math.min(1, challenge.remainingTicks() / (float) active.timeLimitTicks()));
		graphics.fill(x + PAD, barY, x + PAD + barWidth, barY + 3, 0xFF303038);
		graphics.fill(x + PAD, barY, x + PAD + Math.round(barWidth * fraction), barY + 3, ARGB.linearLerp(fraction, 0xFFE53935, 0xFF43A047));

		graphics.text(font, attempts, x + PAD, barY + 3 + 4, DIM, true);
	}

	private static void drawResult(GuiGraphicsExtractor graphics, ClientChallenge challenge, ClientChallenge.Active ended) {
		Font font = Minecraft.getInstance().font;
		ChallengeEndPayload.Outcome outcome = challenge.endOutcome();
		String hanzi = ended.entry().hanzi();
		String title = switch (outcome) {
			case SUCCESS -> "Correct! " + hanzi + " learned";
			case FAILED -> hanzi + " vanished";
			case CANCELLED -> "Challenge stopped";
		};
		int color = switch (outcome) {
			case SUCCESS -> 0xFF66DD66;
			case FAILED -> 0xFFFF6655;
			case CANCELLED -> 0xFFDDDD88;
		};

		if (outcome == ChallengeEndPayload.Outcome.SUCCESS) {
			// Green flash over the whole screen, fading out.
			float fade = Math.max(0, (challenge.endTicks() - (ClientChallenge.END_DISPLAY_TICKS - 15)) / 15f);
			graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), ARGB.color(Math.round(fade * 90), 0x22CC44));
		}

		String detail = challenge.endDetail();
		int width = Math.max(font.width(title) * 2, font.width(detail)) + 2 * PAD;
		int height = PAD + font.lineHeight * 2 + (detail.isEmpty() ? 0 : 4 + font.lineHeight) + PAD;
		int x = (graphics.guiWidth() - width) / 2;
		int y = 6;
		graphics.fill(x, y, x + width, y + height, BACKGROUND);
		graphics.outline(x, y, width, height, color);
		drawScaled(graphics, font, title, x + (width - font.width(title) * 2) / 2f, y + PAD, 2.0f, color);

		if (!detail.isEmpty()) {
			graphics.text(font, detail, x + (width - font.width(detail)) / 2, y + PAD + font.lineHeight * 2 + 4, DIM, true);
		}
	}

	private static String statusLine() {
		return "Say it out loud! (microphone: coming in stage 3)";
	}

	private static void drawMicIndicator(GuiGraphicsExtractor graphics, int x, int y) {
		graphics.fill(x + 1, y, x + 8, y + 8, 0xFF606068);
	}

	private static int pinyinWidth(Font font, List<Pinyin.Syllable> syllables) {
		int width = 0;

		for (int i = 0; i < syllables.size(); i++) {
			width += font.width(syllables.get(i).marked()) + (i > 0 ? font.width(" ") : 0);
		}

		return width;
	}

	/** Draws each syllable in the colour of its tone. */
	private static void drawPinyin(GuiGraphicsExtractor graphics, Font font, List<Pinyin.Syllable> syllables, float x, float y,
			float scale, LangModConfig config) {
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(scale);
		int cursor = 0;

		for (Pinyin.Syllable syllable : syllables) {
			String text = syllable.marked();
			graphics.text(font, text, cursor, 0, config.toneColor(syllable.tone()), true);
			cursor += font.width(text) + font.width(" ");
		}

		graphics.pose().popMatrix();
	}

	static void drawScaled(GuiGraphicsExtractor graphics, Font font, String text, float x, float y, float scale, int color) {
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(scale);
		graphics.text(font, text, 0, 0, color, true);
		graphics.pose().popMatrix();
	}
}
