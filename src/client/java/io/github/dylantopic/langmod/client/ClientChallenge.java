package io.github.dylantopic.langmod.client;

import net.minecraft.world.item.ItemStack;

import io.github.dylantopic.langmod.LangMod;
import io.github.dylantopic.langmod.core.Pinyin;
import io.github.dylantopic.langmod.core.PinyinEntry;
import io.github.dylantopic.langmod.core.lang.Language;
import io.github.dylantopic.langmod.core.lang.NameEntry;
import io.github.dylantopic.langmod.net.AttemptResultPayload;
import io.github.dylantopic.langmod.net.ChallengeEndPayload;
import io.github.dylantopic.langmod.net.ChallengeStartPayload;
import io.github.dylantopic.langmod.net.ChallengeStatePayload;

/**
 * Client-side mirror of the current challenge, fed by server packets and read by the HUD.
 * Only touched on the render thread.
 */
public final class ClientChallenge {
	private static final ClientChallenge INSTANCE = new ClientChallenge();
	/** How long the result of a finished challenge stays on screen. */
	public static final int END_DISPLAY_TICKS = 50;

	/**
	 * The challenge as announced by the server.
	 *
	 * @param pinyin the name's syllables with tones (Mandarin only, null otherwise)
	 */
	public record Active(int id, ItemStack stack, String translationKey, Language language, NameEntry entry, PinyinEntry pinyin,
			int timeLimitTicks, int maxAttempts, boolean longName) {
	}

	private Active active;
	private int remainingTicks;
	private boolean timerRunning;
	private int ticksSinceSync;
	private int attemptsUsed;
	private int queued;
	/** Verdict on the latest answer for the active challenge, or null. */
	private AttemptResultPayload lastResult;

	private Active ended;
	private ChallengeEndPayload.Outcome endOutcome;
	private String endDetail = "";
	private int endTicks;

	/** Kept after the challenge ends, for the debug overlay. */
	private AttemptResultPayload debugResult;
	private NameEntry debugExpected;
	private int debugAudioMs;

	private ClientChallenge() {
	}

	public static ClientChallenge get() {
		return INSTANCE;
	}

	public void onStart(ChallengeStartPayload payload) {
		Language language = Language.byId(payload.language()).orElse(null);
		NameEntry entry = new NameEntry(payload.translationKey(), payload.text(), payload.pronunciation());
		PinyinEntry pinyin = null;

		try {
			if (language == null) {
				throw new IllegalArgumentException("unknown language " + payload.language());
			}

			if (language.tonal()) {
				pinyin = PinyinEntry.of(payload.translationKey(), payload.text(), Pinyin.parseAll(payload.pronunciation()));
			}
		} catch (IllegalArgumentException e) {
			LangMod.LOGGER.error("Bad challenge data from server: {}", e.getMessage());
			return;
		}

		active = new Active(payload.challengeId(), payload.stack(), payload.translationKey(), language, entry, pinyin,
				payload.timeLimitTicks(), payload.maxAttempts(), payload.longName());
		remainingTicks = payload.timeLimitTicks();
		timerRunning = true;
		ticksSinceSync = 0;
		attemptsUsed = 0;
		queued = payload.queued();
		lastResult = null;
		endTicks = 0;
		debugExpected = entry;
		ChallengeController.get().onChallengeStart(active);
	}

	public void onState(ChallengeStatePayload payload) {
		if (active != null && active.id() == payload.challengeId()) {
			remainingTicks = payload.remainingTicks();
			timerRunning = payload.timerRunning();
			attemptsUsed = payload.attemptsUsed();
			queued = payload.queued();
			ticksSinceSync = 0;
		}
	}

	public void onChecking(int audioMs) {
		debugAudioMs = audioMs;
	}

	public void onAttemptResult(AttemptResultPayload payload) {
		if (active != null && active.id() == payload.challengeId()) {
			lastResult = payload;
			attemptsUsed = payload.attemptsUsed();
			debugResult = payload;
			ChallengeController.get().onAttemptResult(payload);
		}
	}

	public void onEnd(ChallengeEndPayload payload) {
		if (active != null && active.id() == payload.challengeId()) {
			ended = active;
			endOutcome = payload.outcome();
			endDetail = payload.detail();
			endTicks = END_DISPLAY_TICKS;
			active = null;
			ChallengeController.get().onChallengeEnd();
		}
	}

	public void tick() {
		// Count down locally between server updates, but stop if the updates stop coming
		// (e.g. the singleplayer game is paused) so the display never runs ahead of the server.
		if (active != null && timerRunning && ticksSinceSync < 20 && remainingTicks > 0) {
			remainingTicks--;
		}

		ticksSinceSync++;

		if (endTicks > 0) {
			endTicks--;
		}
	}

	public void reset() {
		active = null;
		ended = null;
		endTicks = 0;
		lastResult = null;
		ChallengeController.get().onChallengeEnd();
	}

	public Active active() {
		return active;
	}

	public int remainingTicks() {
		return remainingTicks;
	}

	public boolean timerRunning() {
		return timerRunning;
	}

	public int attemptsUsed() {
		return attemptsUsed;
	}

	public int queued() {
		return queued;
	}

	public AttemptResultPayload lastResult() {
		return lastResult;
	}

	public Active ended() {
		return endTicks > 0 ? ended : null;
	}

	public ChallengeEndPayload.Outcome endOutcome() {
		return endOutcome;
	}

	public String endDetail() {
		return endDetail;
	}

	public int endTicks() {
		return endTicks;
	}

	public AttemptResultPayload debugResult() {
		return debugResult;
	}

	public NameEntry debugExpected() {
		return debugExpected;
	}

	public int debugAudioMs() {
		return debugAudioMs;
	}
}
