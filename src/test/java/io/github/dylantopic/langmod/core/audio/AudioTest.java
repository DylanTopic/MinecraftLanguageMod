package io.github.dylantopic.langmod.core.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

class AudioTest {
	private static final Vad.Config CONFIG = new Vad.Config(12f, 800, 5000, 200);

	@Test
	void detectsOneUtterance() {
		Vad vad = new Vad(CONFIG);
		Random random = new Random(1);
		List<Vad.Event> events = new ArrayList<>();
		float[] clip = null;

		// 1 s of background noise (about -60 dB), 1 s of "speech" (220 Hz at about -20 dB), 1.5 s of noise.
		for (int ms = 0; ms < 3500; ms += Vad.FRAME_MS) {
			boolean speaking = ms >= 1000 && ms < 2000;
			Vad.Event event = vad.feed(frame(random, speaking ? 0.14f : 0f, ms));

			if (event != Vad.Event.NONE) {
				events.add(event);
			}

			if (event == Vad.Event.CLIP_READY) {
				clip = vad.takeClip();
			}
		}

		assertEquals(List.of(Vad.Event.SPEECH_START, Vad.Event.CLIP_READY), events);
		assertNotNull(clip);
		double seconds = clip.length / (double) Vad.SAMPLE_RATE;
		// 1 s of speech plus up to 300 ms before and 200 ms after it.
		assertTrue(seconds > 1.1 && seconds < 1.6, "clip length " + seconds);
	}

	@Test
	void ignoresClicksAndStopsLongClips() {
		Vad vad = new Vad(CONFIG);
		Random random = new Random(2);
		List<Vad.Event> events = new ArrayList<>();

		// A 40 ms click never starts a clip; 100 ms of sound starts one that is then discarded.
		for (int ms = 0; ms < 3000; ms += Vad.FRAME_MS) {
			boolean loud = (ms >= 1000 && ms < 1040) || (ms >= 2000 && ms < 2100);
			Vad.Event event = vad.feed(frame(random, loud ? 0.2f : 0f, ms));

			if (event != Vad.Event.NONE) {
				events.add(event);
			}
		}

		assertEquals(List.of(Vad.Event.SPEECH_START, Vad.Event.CLIP_DISCARDED), events);

		// Continuous speech is cut at maxClipMs.
		Vad.Event last = Vad.Event.NONE;
		int ms = 0;

		while (last != Vad.Event.CLIP_READY && ms < 10_000) {
			last = vad.feed(frame(random, 0.2f, ms));
			ms += Vad.FRAME_MS;
		}

		assertEquals(Vad.Event.CLIP_READY, last);
		assertTrue(vad.takeClip().length <= (5000 + 400) * Vad.SAMPLE_RATE / 1000);
	}

	@Test
	void resamples48kStereoTo16kMono() {
		Resampler resampler = new Resampler(48_000, 2, 16_000);
		int frames = 48_000; // one second
		byte[] pcm = new byte[frames * 4];

		for (int i = 0; i < frames; i++) {
			short value = (short) (Math.sin(2 * Math.PI * 440 * i / 48_000.0) * 16000);
			for (int c = 0; c < 2; c++) {
				pcm[(i * 2 + c) * 2] = (byte) value;
				pcm[(i * 2 + c) * 2 + 1] = (byte) (value >> 8);
			}
		}

		float[] out = new float[0];
		// Feed in uneven chunks, as a microphone would deliver them.
		for (int offset = 0; offset < pcm.length; ) {
			int chunk = Math.min(4 * 1234, pcm.length - offset);
			byte[] part = java.util.Arrays.copyOfRange(pcm, offset, offset + chunk);
			float[] produced = resampler.process(part, part.length);
			float[] joined = java.util.Arrays.copyOf(out, out.length + produced.length);
			System.arraycopy(produced, 0, joined, out.length, produced.length);
			out = joined;
			offset += chunk;
		}

		assertTrue(Math.abs(out.length - 16_000) <= 2, "got " + out.length + " samples");
		// The 440 Hz tone survives with roughly the same amplitude (0.49).
		float peak = 0;

		for (int i = 1000; i < out.length; i++) {
			peak = Math.max(peak, Math.abs(out[i]));
		}

		assertTrue(peak > 0.4f && peak < 0.55f, "peak " + peak);
	}

	@Test
	void encodesWav() {
		byte[] wav = Wav.encode(new float[] {0f, 1f, -1f}, 16_000);
		assertEquals(44 + 6, wav.length);
		assertEquals('R', wav[0]);
		assertEquals((byte) 0xFF, wav[46]);
		assertEquals((byte) 0x7F, wav[47]);
	}

	@Test
	void readsWav() {
		Wav.Audio audio = Wav.decode(Wav.encode(new float[8_000], 16_000));
		assertEquals(16_000, audio.sampleRate());
		assertEquals(1, audio.channels());
		assertEquals(16, audio.bitsPerSample());
		assertEquals(16_000, audio.data().length);
		assertEquals(500, audio.millis());
	}

	/** One 20 ms frame: low noise plus an optional 220 Hz tone. */
	private static float[] frame(Random random, float toneAmplitude, int startMs) {
		float[] frame = new float[Vad.FRAME_SAMPLES];
		int start = startMs * Vad.SAMPLE_RATE / 1000;

		for (int i = 0; i < frame.length; i++) {
			float noise = (float) (random.nextGaussian() * 0.001);
			frame[i] = noise + toneAmplitude * (float) Math.sin(2 * Math.PI * 220 * (start + i) / Vad.SAMPLE_RATE);
		}

		return frame;
	}
}
