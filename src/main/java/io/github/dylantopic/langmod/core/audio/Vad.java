package io.github.dylantopic.langmod.core.audio;

/**
 * Simple energy-based voice activity detection on 16 kHz mono audio.
 *
 * <p>It tracks the background noise level and treats anything clearly louder as speech. A clip
 * starts once speech has lasted {@link #START_FRAMES} frames (keeping a little audio from before
 * the start) and ends after {@code silenceMs} of quiet or at {@code maxClipMs}.
 *
 * <p>Feed it frames of exactly {@link #FRAME_SAMPLES} samples.
 */
public final class Vad {
	public static final int SAMPLE_RATE = 16_000;
	/** 20 ms frames. */
	public static final int FRAME_SAMPLES = 320;
	public static final int FRAME_MS = 20;
	/** Consecutive loud frames needed to start a clip (60 ms), so single clicks are ignored. */
	static final int START_FRAMES = 3;
	/** Audio kept from before the speech started, so the first consonant is not cut off. */
	static final int PRE_ROLL_MS = 300;
	/** Quiet kept at the end of a clip. */
	static final int TAIL_MS = 200;
	/** Never treat anything quieter than this as speech, however quiet the room is. */
	static final float ABSOLUTE_MIN_DB = -50f;

	public enum Event {
		/** Nothing happened. */
		NONE,
		/** Speech started; a clip is being recorded. */
		SPEECH_START,
		/** A clip is complete; collect it with {@link #takeClip()}. */
		CLIP_READY,
		/** Something started but was too short to be speech. */
		CLIP_DISCARDED
	}

	public record Config(float thresholdDb, int silenceMs, int maxClipMs, int minSpeechMs) {
	}

	private final Config config;
	private final float[] preRoll = new float[PRE_ROLL_MS * SAMPLE_RATE / 1000];
	private int preRollFill;
	private float[] clip = new float[0];
	private int clipLength;
	private float noiseFloorDb = Float.NaN;
	private float levelDb = -100f;
	private int loudFrames;
	private int silentMs;
	private int speechMs;
	private boolean inSpeech;
	private float[] readyClip;

	public Vad(Config config) {
		this.config = config;
	}

	public Event feed(float[] frame) {
		if (frame.length != FRAME_SAMPLES) {
			throw new IllegalArgumentException("Expected " + FRAME_SAMPLES + " samples, got " + frame.length);
		}

		levelDb = levelDb(frame);

		if (Float.isNaN(noiseFloorDb)) {
			noiseFloorDb = levelDb;
		}

		float threshold = threshold();

		if (!inSpeech) {
			if (levelDb >= threshold) {
				loudFrames++;
			} else {
				loudFrames = 0;
				// Follow the background noise: quickly downwards, slowly upwards.
				float rate = levelDb < noiseFloorDb ? 0.2f : 0.02f;
				noiseFloorDb += (levelDb - noiseFloorDb) * rate;
			}

			if (loudFrames >= START_FRAMES) {
				startClip();
				append(frame);
				return Event.SPEECH_START;
			}

			pushPreRoll(frame);
			return Event.NONE;
		}

		append(frame);

		// A little hysteresis so speech that dips slightly below the start threshold keeps going.
		if (levelDb >= threshold - 3f) {
			silentMs = 0;
			speechMs += FRAME_MS;
		} else {
			silentMs += FRAME_MS;
		}

		boolean tooLong = clipLength * 1000L / SAMPLE_RATE >= config.maxClipMs();

		if (silentMs >= config.silenceMs() || tooLong) {
			return endClip();
		}

		return Event.NONE;
	}

	/** The finished clip after {@link Event#CLIP_READY}, then forgets it. */
	public float[] takeClip() {
		float[] result = readyClip;
		readyClip = null;
		return result;
	}

	/** Forgets any speech in progress (e.g. while the previous clip is being transcribed). */
	public void reset() {
		inSpeech = false;
		loudFrames = 0;
		clipLength = 0;
		preRollFill = 0;
		readyClip = null;
	}

	public boolean inSpeech() {
		return inSpeech;
	}

	public float levelDb() {
		return levelDb;
	}

	public float noiseFloorDb() {
		return Float.isNaN(noiseFloorDb) ? -100f : noiseFloorDb;
	}

	public float threshold() {
		return Math.max(noiseFloorDb() + config.thresholdDb(), ABSOLUTE_MIN_DB);
	}

	public static float levelDb(float[] frame) {
		double sum = 0;

		for (float sample : frame) {
			sum += sample * sample;
		}

		double rms = Math.sqrt(sum / frame.length);
		return (float) (20 * Math.log10(rms + 1e-9));
	}

	private void startClip() {
		inSpeech = true;
		silentMs = 0;
		// The frames that triggered the start count as speech already.
		speechMs = (START_FRAMES - 1) * FRAME_MS;
		clip = new float[SAMPLE_RATE * Math.max(config.maxClipMs(), 1000) / 1000 + preRoll.length + FRAME_SAMPLES];
		System.arraycopy(preRoll, 0, clip, 0, preRollFill);
		clipLength = preRollFill;
		preRollFill = 0;
	}

	private Event endClip() {
		inSpeech = false;
		loudFrames = 0;
		int trailingSilence = Math.max(0, silentMs - TAIL_MS) * SAMPLE_RATE / 1000;
		int length = Math.max(0, clipLength - trailingSilence);

		if (speechMs < config.minSpeechMs()) {
			clipLength = 0;
			return Event.CLIP_DISCARDED;
		}

		readyClip = new float[length];
		System.arraycopy(clip, 0, readyClip, 0, length);
		clipLength = 0;
		return Event.CLIP_READY;
	}

	private void append(float[] frame) {
		int space = clip.length - clipLength;
		int n = Math.min(space, frame.length);
		System.arraycopy(frame, 0, clip, clipLength, n);
		clipLength += n;
	}

	private void pushPreRoll(float[] frame) {
		if (preRollFill + frame.length > preRoll.length) {
			int drop = preRollFill + frame.length - preRoll.length;
			System.arraycopy(preRoll, drop, preRoll, 0, preRollFill - drop);
			preRollFill -= drop;
		}

		System.arraycopy(frame, 0, preRoll, preRollFill, frame.length);
		preRollFill += frame.length;
	}
}
