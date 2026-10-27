package io.github.dylantopic.langmod.core.audio;

/**
 * Streaming conversion of microphone audio (any sample rate, mono or interleaved stereo, 16-bit
 * signed little endian) to 16 kHz mono floats in [-1, 1], as Whisper expects.
 *
 * <p>Downsampling uses a windowed-sinc low-pass filter (to avoid aliasing) followed by linear
 * interpolation, which is plenty for speech recognition.
 */
public final class Resampler {
	private static final int TAPS = 31;

	private final int channels;
	private final double step;
	private final float[] filter;
	/** Recent mono input samples, used as filter history across calls. */
	private final float[] history = new float[TAPS];
	private int historyFill;
	private double position;
	private float previousFiltered;
	private boolean havePrevious;

	public Resampler(int sourceRate, int channels, int targetRate) {
		if (sourceRate <= 0 || targetRate <= 0 || channels < 1) {
			throw new IllegalArgumentException("Bad audio format: " + sourceRate + " Hz, " + channels + " channels");
		}

		this.channels = channels;
		this.step = sourceRate / (double) targetRate;
		this.filter = sourceRate > targetRate ? lowPass(0.5 * targetRate / sourceRate * 0.9) : null;
	}

	/**
	 * Converts {@code length} bytes of PCM from {@code pcm}. Returns the new 16 kHz samples
	 * (possibly none); leftover state is kept for the next call.
	 */
	public float[] process(byte[] pcm, int length) {
		int frames = length / (2 * channels);
		float[] out = new float[(int) (frames / step) + 2];
		int count = 0;

		for (int f = 0; f < frames; f++) {
			float mono = 0;

			for (int c = 0; c < channels; c++) {
				int i = (f * channels + c) * 2;
				short sample = (short) ((pcm[i] & 0xFF) | (pcm[i + 1] << 8));
				mono += sample / 32768f;
			}

			mono /= channels;
			float filtered = filter == null ? mono : filtered(mono);

			if (!havePrevious) {
				previousFiltered = filtered;
				havePrevious = true;
				continue;
			}

			// Emit every output sample whose position falls between the previous and this input sample.
			while (position <= 1.0) {
				out[count++] = (float) (previousFiltered + (filtered - previousFiltered) * position);
				position += step;

				if (count == out.length) {
					out = java.util.Arrays.copyOf(out, out.length * 2);
				}
			}

			position -= 1.0;
			previousFiltered = filtered;
		}

		return java.util.Arrays.copyOf(out, count);
	}

	private float filtered(float sample) {
		if (historyFill < TAPS) {
			history[historyFill++] = sample;
		} else {
			System.arraycopy(history, 1, history, 0, TAPS - 1);
			history[TAPS - 1] = sample;
		}

		double sum = 0;
		int offset = TAPS - historyFill;

		for (int i = 0; i < historyFill; i++) {
			sum += history[i] * filter[offset + i];
		}

		return (float) sum;
	}

	/** Windowed-sinc low-pass FIR; {@code cutoff} is a fraction of the source sample rate. */
	private static float[] lowPass(double cutoff) {
		float[] taps = new float[TAPS];
		double sum = 0;
		int middle = TAPS / 2;

		for (int i = 0; i < TAPS; i++) {
			int n = i - middle;
			double sinc = n == 0 ? 2 * cutoff : Math.sin(2 * Math.PI * cutoff * n) / (Math.PI * n);
			double window = 0.54 - 0.46 * Math.cos(2 * Math.PI * i / (TAPS - 1));
			taps[i] = (float) (sinc * window);
			sum += taps[i];
		}

		for (int i = 0; i < TAPS; i++) {
			taps[i] /= (float) sum;
		}

		return taps;
	}
}
