package io.github.dylantopic.langmod.client.hud;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import io.github.dylantopic.langmod.client.ChallengeController;
import io.github.dylantopic.langmod.client.ClientChallenge;
import io.github.dylantopic.langmod.client.audio.MicCapture;
import io.github.dylantopic.langmod.client.speech.SpeechService;
import io.github.dylantopic.langmod.core.PinyinEntry;
import io.github.dylantopic.langmod.net.AttemptResultPayload;

/**
 * Debug overlay (toggle with /langmod debug or its key): raw Whisper output, the simplified text,
 * its pinyin, the expected pinyin, the verdict and the timings of the last answer.
 */
public final class DebugHud {
	private DebugHud() {
	}

	public static void draw(GuiGraphicsExtractor graphics) {
		Font font = Minecraft.getInstance().font;
		ClientChallenge challenge = ClientChallenge.get();
		SpeechService speech = SpeechService.get();
		MicCapture mic = ChallengeController.get().mic();
		AttemptResultPayload result = challenge.debugResult();
		PinyinEntry expected = challenge.debugExpected();
		List<String> lines = new ArrayList<>();

		lines.add("LangMod debug");
		lines.add("Speech: " + (speech.ready() ? speech.description() : speech.status() + " - " + speech.statusMessage()));
		lines.add(mic == null ? "Mic: closed" : String.format(Locale.ROOT, "Mic: %s  level %.0f dB, threshold %.0f dB%s",
				mic.deviceName(), mic.levelDb(), mic.thresholdDb(), mic.inSpeech() ? "  (speech)" : ""));

		if (result == null) {
			lines.add("No answer yet.");
		} else {
			lines.add("Raw:        \"" + result.raw() + "\"");
			lines.add("Simplified: " + (result.heard().isEmpty() ? "(nothing)" : result.heard()));
			lines.add("Its pinyin: " + heardPinyin(result));
			lines.add("Expected:   " + (expected == null ? "?" : expected.hanzi() + "  " + expected.marks()));
			lines.add("Result:     " + result.verdict()
					+ (result.mode().isEmpty() ? "" : "  (" + result.errors() + " error(s), " + result.allowedErrors() + " allowed, " + result.mode() + ")"));
			lines.add(String.format(Locale.ROOT, "Timing:     audio %d ms, transcription %d ms", challenge.debugAudioMs(), result.millis()));

			if (!result.note().isEmpty()) {
				lines.add("Note:       " + result.note());
			}
		}

		int width = 0;

		for (String line : lines) {
			width = Math.max(width, font.width(line));
		}

		int x = 4;
		int y = graphics.guiHeight() / 3;
		graphics.fill(x - 2, y - 2, x + width + 2, y + lines.size() * (font.lineHeight + 1) + 1, 0xB0000000);

		for (String line : lines) {
			graphics.text(font, line, x, y, 0xFFE0E0E0, false);
			y += font.lineHeight + 1;
		}
	}

	private static String heardPinyin(AttemptResultPayload result) {
		List<String> parts = new ArrayList<>();

		for (AttemptResultPayload.Token token : result.heardTokens()) {
			parts.add(token.pinyin() + "(" + token.status().name().toLowerCase(Locale.ROOT) + ")");
		}

		return parts.isEmpty() ? "-" : String.join(" ", parts);
	}
}
