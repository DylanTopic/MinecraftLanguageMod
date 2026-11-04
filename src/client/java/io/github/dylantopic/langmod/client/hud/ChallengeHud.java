package io.github.dylantopic.langmod.client.hud;

import java.util.List;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;

import io.github.dylantopic.langmod.LangModConfig;
import io.github.dylantopic.langmod.client.ChallengeController;
import io.github.dylantopic.langmod.client.ClientChallenge;
import io.github.dylantopic.langmod.client.MicTest;
import io.github.dylantopic.langmod.client.audio.MicCapture;
import io.github.dylantopic.langmod.core.Matcher;
import io.github.dylantopic.langmod.core.Pinyin;
import io.github.dylantopic.langmod.core.lang.Language;
import io.github.dylantopic.langmod.core.lang.NameEntry;
import io.github.dylantopic.langmod.net.AttemptResultPayload;
import io.github.dylantopic.langmod.net.ChallengeEndPayload;

/**
 * The challenge popup: item icon, the Chinese name in large characters, tone-coloured pinyin,
 * the English name, microphone state, the countdown and, after an answer, what Whisper heard
 * with every syllable marked right or wrong.
 */
public final class ChallengeHud {
	private static final int PAD = 6;
	private static final int ICON = 32;
	private static final int BACKGROUND = 0xD0101018;
	private static final int BORDER = 0xFF5A5A78;
	private static final int TEXT = 0xFFFFFFFF;
	private static final int DIM = 0xFFA0A0A8;
	private static final int NOTE = 0xFFFFDD66;
	private static final int ROMANIZATION = 0xFFB8D4FF;

	private ChallengeHud() {
	}

	public static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		ClientChallenge challenge = ClientChallenge.get();

		if (MicTest.current() != null) {
			MicIndicator.drawMicTest(graphics, MicTest.current());
		} else if (challenge.active() != null) {
			drawChallenge(graphics, challenge, challenge.active());
		} else if (challenge.ended() != null) {
			drawResult(graphics, challenge, challenge.ended());
		}

		if (LangModConfig.get().debugOverlay) {
			DebugHud.draw(graphics);
		}
	}

	/** Colour for how a syllable compared. */
	public static int statusColor(Matcher.Status status) {
		return switch (status) {
			case MATCH -> 0xFF66DD66;
			case TONE -> 0xFFFFAA33;
			case WRONG, MISSING -> 0xFFFF5555;
			case EXTRA -> 0xFFCC8888;
		};
	}

	private static void drawChallenge(GuiGraphicsExtractor graphics, ClientChallenge challenge, ClientChallenge.Active active) {
		Minecraft minecraft = Minecraft.getInstance();
		Font font = minecraft.font;
		LangModConfig config = LangModConfig.get();
		ChallengeController controller = ChallengeController.get();
		NameEntry entry = active.entry();
		Language language = active.language();
		AttemptResultPayload result = challenge.lastResult();

		float nameScale = fitScale(font, entry.text(), config.hanziScale, graphics.guiWidth());
		float pinyinScale = config.pinyinScale;
		// Mandarin: pinyin coloured by tone; Korean and Russian: romanization; Japanese: reading and romaji;
		// languages written in Latin letters: nothing. Long ones are shrunk to fit, like the name.
		String romanization = language.tonal() || entry.pronunciation().isEmpty() ? null : entry.pronunciation();
		float romanizationScale = romanization == null ? pinyinScale : fitScale(font, romanization, pinyinScale, graphics.guiWidth());
		String english = config.showEnglish ? Component.translatable(active.translationKey()).getString() : null;

		if (english != null && english.equalsIgnoreCase(entry.text())) {
			english = null; // learning the game's own language: the line would only repeat the name
		}

		int nameWidth = Math.round(font.width(entry.text()) * nameScale);
		int pronunciationWidth = language.tonal() ? Math.round(pinyinWidth(font, active.pinyin().syllables()) * pinyinScale)
				: romanization == null ? 0 : Math.round(font.width(romanization) * romanizationScale);
		int englishWidth = english == null ? 0 : font.width(english);
		int textWidth = Math.max(nameWidth, Math.max(pronunciationWidth, englishWidth));

		String status = statusLine(controller);
		String timer = (challenge.remainingTicks() + 19) / 20 + "s";
		String attempts = "Attempt " + Math.min(challenge.attemptsUsed() + 1, active.maxAttempts()) + "/" + active.maxAttempts()
				+ (active.longName() ? "  (long name: one slip allowed)" : "")
				+ (challenge.queued() > 0 ? "  +" + challenge.queued() + " queued" : "");
		int footerWidth = Math.max(12 + font.width(status) + 8 + font.width(timer), font.width(attempts));

		boolean showHeard = result != null && !result.heardTokens().isEmpty() && result.verdict() == AttemptResultPayload.Verdict.WRONG;
		String note = result == null ? "" : result.note();
		int heardWidth = showHeard ? heardWidth(font, result, language) : 0;

		int width = Math.max(ICON + PAD + textWidth, Math.max(Math.max(footerWidth, heardWidth), Math.max(font.width(note), 170))) + 2 * PAD;
		int nameHeight = Math.round(font.lineHeight * nameScale);
		int pronunciationHeight = language.tonal() ? Math.round(font.lineHeight * pinyinScale)
				: romanization != null ? Math.round(font.lineHeight * romanizationScale) : 0;
		int textHeight = nameHeight + (pronunciationHeight > 0 ? 3 + pronunciationHeight : 0) + (english == null ? 0 : 3 + font.lineHeight);
		int headerHeight = Math.max(ICON, textHeight);
		int resultHeight = (showHeard ? font.lineHeight + 3 : 0) + (note.isEmpty() ? 0 : font.lineHeight + 3);
		int height = PAD + headerHeight + 6 + font.lineHeight + 4 + 3 + 4 + font.lineHeight + (resultHeight > 0 ? 4 + resultHeight : 0) + PAD;

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

		// Name, then pinyin coloured by tone or romanization, then English. Whatever the last answer
		// got wrong is underlined: the pinyin syllables in Mandarin, the words or syllables of the name otherwise.
		int textX = x + PAD + ICON + PAD;
		int textY = y + PAD + (headerHeight - textHeight) / 2;
		List<Matcher.Status> statuses = result == null ? List.of() : result.expectedStatus();
		drawScaled(graphics, font, entry.text(), textX, textY, nameScale, TEXT);

		if (!language.tonal()) {
			underlineUnits(graphics, font, language, entry.text(), statuses, textX, textY, nameScale);
		}

		textY += nameHeight + 3;

		if (language.tonal()) {
			drawPinyin(graphics, font, active.pinyin().syllables(), statuses, textX, textY, pinyinScale, config);
			textY += pronunciationHeight + 3;
		} else if (romanization != null) {
			drawScaled(graphics, font, romanization, textX, textY, romanizationScale, ROMANIZATION);
			textY += pronunciationHeight + 3;
		}

		if (english != null) {
			graphics.text(font, english, textX, textY, DIM, true);
		}

		// Microphone indicator, status text and countdown.
		int rowY = y + PAD + headerHeight + 6;
		MicCapture mic = controller.mic();
		boolean listening = mic != null && !controller.checking() && !controller.hearing();
		MicIndicator.drawDot(graphics, x + PAD, rowY, listening, mic != null && mic.inSpeech());
		graphics.text(font, status, x + PAD + 12, rowY, TEXT, true);
		graphics.text(font, timer, x + width - PAD - font.width(timer), rowY, challenge.timerRunning() ? TEXT : DIM, true);

		// Time bar (green -> red) with the microphone level just above it.
		int barY = rowY + font.lineHeight + 4;
		int barWidth = width - 2 * PAD;

		if (mic != null) {
			MicIndicator.drawMeter(graphics, x + PAD + 12, barY - 4, Math.min(80, barWidth - 12), mic);
		}

		float fraction = active.timeLimitTicks() <= 0 ? 0 : Math.max(0, Math.min(1, challenge.remainingTicks() / (float) active.timeLimitTicks()));
		graphics.fill(x + PAD, barY + 1, x + PAD + barWidth, barY + 3, 0xFF303038);
		graphics.fill(x + PAD, barY + 1, x + PAD + Math.round(barWidth * fraction), barY + 3, ARGB.linearLerp(fraction, 0xFFE53935, 0xFF43A047));

		int lineY = barY + 3 + 4;
		graphics.text(font, attempts, x + PAD, lineY, DIM, true);
		lineY += font.lineHeight + 4;

		if (showHeard) {
			drawHeard(graphics, font, result, language, x + PAD, lineY);
			lineY += font.lineHeight + 3;
		}

		if (!note.isEmpty()) {
			graphics.text(font, note, x + PAD, lineY, NOTE, true);
		}
	}

	private static void drawResult(GuiGraphicsExtractor graphics, ClientChallenge challenge, ClientChallenge.Active ended) {
		Font font = Minecraft.getInstance().font;
		ChallengeEndPayload.Outcome outcome = challenge.endOutcome();
		String name = ended.entry().text();
		String title = switch (outcome) {
			case SUCCESS -> correct(ended.language()) + " " + name + " learned";
			case FAILED -> name + " vanished";
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
		float titleScale = Math.min(2.0f, (graphics.guiWidth() - 2 * PAD - 12) / (float) Math.max(1, font.width(title)));
		int titleWidth = Math.round(font.width(title) * titleScale);
		int titleHeight = Math.round(font.lineHeight * titleScale);
		int width = Math.max(titleWidth, font.width(detail)) + 2 * PAD;
		int height = PAD + titleHeight + (detail.isEmpty() ? 0 : 4 + font.lineHeight) + PAD;
		int x = (graphics.guiWidth() - width) / 2;
		int y = 6;
		graphics.fill(x, y, x + width, y + height, BACKGROUND);
		graphics.outline(x, y, width, height, color);
		drawScaled(graphics, font, title, x + (width - titleWidth) / 2f, y + PAD, titleScale, color);

		if (!detail.isEmpty()) {
			graphics.text(font, detail, x + (width - font.width(detail)) / 2, y + PAD + titleHeight + 4, DIM, true);
		}
	}

	private static String statusLine(ChallengeController controller) {
		if (!controller.micAvailable()) {
			return "Microphone unavailable";
		}

		if (controller.hearing()) {
			return "Listen...";
		}

		if (controller.checking()) {
			return "Checking...";
		}

		MicCapture mic = controller.mic();

		if (mic == null) {
			return "Opening microphone...";
		}

		return mic.inSpeech() ? "Listening..." : "Say it out loud!";
	}

	private static int heardWidth(Font font, AttemptResultPayload result, Language language) {
		int width = font.width("Heard: ");

		for (AttemptResultPayload.Token token : result.heardTokens()) {
			width += font.width(token.text()) + gap(font, language);
		}

		if (language.tonal()) {
			width += font.width("  ");

			for (AttemptResultPayload.Token token : result.heardTokens()) {
				width += font.width(token.pronunciation() + " ");
			}
		} else if (!result.heardPronunciation().isEmpty()) {
			width += font.width("  (" + result.heardPronunciation() + ")");
		}

		return width;
	}

	/**
	 * "Heard: 平国  píng guó", "Heard: lana roja" or "Heard: 사가  (saga)", with every character,
	 * word or syllable (and in Mandarin its pinyin) coloured by how it compared.
	 */
	private static void drawHeard(GuiGraphicsExtractor graphics, Font font, AttemptResultPayload result, Language language, int x, int y) {
		graphics.text(font, "Heard: ", x, y, DIM, true);
		int cursor = x + font.width("Heard: ");

		for (AttemptResultPayload.Token token : result.heardTokens()) {
			graphics.text(font, token.text(), cursor, y, statusColor(token.status()), true);
			cursor += font.width(token.text()) + gap(font, language);
		}

		if (language.tonal()) {
			cursor += font.width("  ");

			for (AttemptResultPayload.Token token : result.heardTokens()) {
				graphics.text(font, token.pronunciation(), cursor, y, statusColor(token.status()), true);
				cursor += font.width(token.pronunciation() + " ");
			}
		} else if (!result.heardPronunciation().isEmpty()) {
			graphics.text(font, "  (" + result.heardPronunciation() + ")", cursor, y, DIM, true);
		}
	}

	/** Space between heard units: a word space in Spanish and French, 1 pixel between characters and syllables. */
	private static int gap(Font font, Language language) {
		return language == Language.SPANISH || language == Language.FRENCH ? font.width(" ") : 1;
	}

	/** "Correct!" in the language being learned. */
	private static String correct(Language language) {
		return switch (language) {
			case MANDARIN -> "正确!";
			case SPANISH -> "¡Correcto!";
			case FRENCH -> "Bravo !";
			case KOREAN -> "정답!";
			case RUSSIAN -> "Правильно!";
			case DUTCH -> "Goed zo!";
			case LATIN -> "Recte!";
			case FILIPINO -> "Tama!";
			case JAPANESE -> "正解！";
			case BOSNIAN -> "Tačno!";
			case PORTUGUESE -> "Correto!";
			case VIETNAMESE -> "Chính xác!";
			case GERMAN -> "Richtig!";
			case ENGLISH -> "Correct!";
		};
	}

	/** The name's scale: as configured, but small enough for long names to fit on screen (never below 1). */
	private static float fitScale(Font font, String name, float preferred, int screenWidth) {
		float available = Math.max(80, screenWidth - 12 - ICON - 3 * PAD);
		return Math.max(1.0f, Math.min(preferred, available / Math.max(1, font.width(name))));
	}

	/** Underlines the words or syllables of a name ({@link Language#units}) that the last answer got wrong. */
	private static void underlineUnits(GuiGraphicsExtractor graphics, Font font, Language language, String name,
			List<Matcher.Status> statuses, float x, float y, float scale) {
		List<String> units = language.units(name);
		int from = 0;
		int lineY = Math.round(y + font.lineHeight * scale);

		for (int i = 0; i < units.size() && i < statuses.size(); i++) {
			int at = name.indexOf(units.get(i), from);

			if (at < 0) {
				break;
			}

			from = at + units.get(i).length();

			if (statuses.get(i) != Matcher.Status.MATCH) {
				int left = Math.round(x + font.width(name.substring(0, at)) * scale);
				int right = Math.round(x + font.width(name.substring(0, from)) * scale);
				graphics.fill(left, lineY, right, lineY + 1, statusColor(statuses.get(i)));
			}
		}
	}

	private static int pinyinWidth(Font font, List<Pinyin.Syllable> syllables) {
		int width = 0;

		for (int i = 0; i < syllables.size(); i++) {
			width += font.width(syllables.get(i).marked()) + (i > 0 ? font.width(" ") : 0);
		}

		return width;
	}

	/** Draws each syllable in the colour of its tone, underlined in red/orange if the last answer got it wrong. */
	private static void drawPinyin(GuiGraphicsExtractor graphics, Font font, List<Pinyin.Syllable> syllables, List<Matcher.Status> statuses,
			float x, float y, float scale, LangModConfig config) {
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(scale);
		int cursor = 0;

		for (int i = 0; i < syllables.size(); i++) {
			Pinyin.Syllable syllable = syllables.get(i);
			String text = syllable.marked();
			int textWidth = font.width(text);
			graphics.text(font, text, cursor, 0, config.toneColor(syllable.tone()), true);

			if (i < statuses.size() && statuses.get(i) != Matcher.Status.MATCH) {
				graphics.fill(cursor, font.lineHeight, cursor + textWidth, font.lineHeight + 1, statusColor(statuses.get(i)));
			}

			cursor += textWidth + font.width(" ");
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
