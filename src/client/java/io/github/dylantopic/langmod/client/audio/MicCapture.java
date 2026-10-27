package io.github.dylantopic.langmod.client.audio;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Line;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;

import io.github.dylantopic.langmod.LangMod;
import io.github.dylantopic.langmod.core.audio.Resampler;
import io.github.dylantopic.langmod.core.audio.Vad;

/**
 * Records from a microphone on its own thread, converts to 16 kHz mono and runs voice activity
 * detection. The microphone is only open while an instance exists: create one when listening
 * starts and {@link #close()} it as soon as listening ends.
 *
 * <p>Listener methods are called on the capture thread.
 */
public final class MicCapture implements AutoCloseable {
	public interface Listener {
		/** Somebody started speaking. */
		void onSpeechStart();

		/** An utterance is complete (16 kHz mono). */
		void onClip(float[] samples);

		/** Something started but was too short to be speech (a click or bump). */
		default void onDiscarded() {
		}

		/** The microphone failed; it is closed. */
		void onError(String message);
	}

	private static final AudioFormat[] FORMATS = {
			new AudioFormat(16_000f, 16, 1, true, false),
			new AudioFormat(48_000f, 16, 1, true, false),
			new AudioFormat(44_100f, 16, 1, true, false),
			new AudioFormat(48_000f, 16, 2, true, false),
			new AudioFormat(44_100f, 16, 2, true, false),
	};

	private final TargetDataLine line;
	private final String deviceName;
	private final Vad vad;
	private final Listener listener;
	private final Thread thread;
	private volatile boolean running = true;
	private volatile boolean paused;
	private volatile float levelDb = -100f;
	private volatile float thresholdDb = -50f;
	private volatile boolean inSpeech;

	private MicCapture(TargetDataLine line, String deviceName, Vad.Config vadConfig, Listener listener) {
		this.line = line;
		this.deviceName = deviceName;
		this.vad = new Vad(vadConfig);
		this.listener = listener;
		this.thread = new Thread(this::run, "LangMod-Mic");
		this.thread.setDaemon(true);
	}

	/**
	 * Opens the microphone and starts listening.
	 *
	 * @param preferredDevice part of a device name, or empty for the system default
	 */
	public static MicCapture open(String preferredDevice, Vad.Config vadConfig, Listener listener) throws LineUnavailableException {
		LineUnavailableException last = null;

		for (Mixer.Info mixer : candidateMixers(preferredDevice)) {
			for (AudioFormat format : FORMATS) {
				DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);

				try {
					TargetDataLine line;
					String name;

					if (mixer == null) {
						if (!AudioSystem.isLineSupported(info)) {
							continue;
						}

						line = (TargetDataLine) AudioSystem.getLine(info);
						name = "system default";
					} else {
						Mixer m = AudioSystem.getMixer(mixer);

						if (!m.isLineSupported(info)) {
							continue;
						}

						line = (TargetDataLine) m.getLine(info);
						name = mixer.getName();
					}

					// About 100 ms of buffer; we read in 20 ms chunks.
					int frameBytes = format.getFrameSize();
					line.open(format, Math.max(frameBytes * (int) (format.getSampleRate() / 10), 4096));
					line.start();
					MicCapture capture = new MicCapture(line, name + " @ " + (int) format.getSampleRate() + " Hz", vadConfig, listener);
					capture.thread.start();
					LangMod.LOGGER.info("Microphone open: {}", capture.deviceName);
					return capture;
				} catch (LineUnavailableException | IllegalArgumentException | SecurityException e) {
					last = e instanceof LineUnavailableException lue ? lue : new LineUnavailableException(e.getMessage());
				}
			}
		}

		throw last != null ? last : new LineUnavailableException("No microphone found");
	}

	/** Names of all devices Java can record from. */
	public static List<String> deviceNames() {
		List<String> names = new ArrayList<>();

		for (Mixer.Info info : AudioSystem.getMixerInfo()) {
			if (supportsCapture(info)) {
				names.add(info.getName());
			}
		}

		return names;
	}

	/** While paused, audio is read and thrown away (the line stays open, nothing is recorded). */
	public void setPaused(boolean paused) {
		this.paused = paused;
	}

	public String deviceName() {
		return deviceName;
	}

	/** Current input level in dBFS, for meters. */
	public float levelDb() {
		return levelDb;
	}

	/** Level above which sound counts as speech. */
	public float thresholdDb() {
		return thresholdDb;
	}

	public boolean inSpeech() {
		return inSpeech;
	}

	@Override
	public void close() {
		running = false;
		line.stop();
		line.close();
		LangMod.LOGGER.info("Microphone closed");
	}

	private void run() {
		AudioFormat format = line.getFormat();
		Resampler resampler = new Resampler((int) format.getSampleRate(), format.getChannels(), Vad.SAMPLE_RATE);
		byte[] buffer = new byte[format.getFrameSize() * (int) (format.getSampleRate() / 50)];
		float[] frame = new float[Vad.FRAME_SAMPLES];
		int filled = 0;
		boolean wasPaused = false;

		try {
			while (running) {
				int read = line.read(buffer, 0, buffer.length);

				if (read <= 0) {
					continue;
				}

				for (float sample : resampler.process(buffer, read)) {
					frame[filled++] = sample;

					if (filled < frame.length) {
						continue;
					}

					filled = 0;
					levelDb = Vad.levelDb(frame);

					if (paused) {
						wasPaused = true;
						inSpeech = false;
						continue;
					}

					if (wasPaused) {
						vad.reset();
						wasPaused = false;
					}

					Vad.Event event = vad.feed(frame);
					thresholdDb = vad.threshold();
					inSpeech = vad.inSpeech();

					switch (event) {
						case SPEECH_START -> listener.onSpeechStart();
						case CLIP_READY -> listener.onClip(vad.takeClip());
						case CLIP_DISCARDED -> listener.onDiscarded();
						case NONE -> {
						}
					}
				}
			}
		} catch (RuntimeException e) {
			if (running) {
				LangMod.LOGGER.error("Microphone failed", e);
				running = false;
				line.close();
				listener.onError(e.getMessage() == null ? e.toString() : e.getMessage());
			}
		}
	}

	private static List<Mixer.Info> candidateMixers(String preferred) {
		List<Mixer.Info> result = new ArrayList<>();
		String wanted = preferred == null ? "" : preferred.trim().toLowerCase(Locale.ROOT);

		if (!wanted.isEmpty()) {
			for (Mixer.Info info : AudioSystem.getMixerInfo()) {
				String name = (info.getName() + " " + info.getDescription()).toLowerCase(Locale.ROOT);

				if (name.contains(wanted) && supportsCapture(info)) {
					result.add(info);
				}
			}

			if (result.isEmpty()) {
				LangMod.LOGGER.warn("No microphone matching '{}' (have: {}); using the default", preferred, deviceNames());
			}
		}

		result.add(null); // system default
		return result;
	}

	private static boolean supportsCapture(Mixer.Info info) {
		try {
			return AudioSystem.getMixer(info).isLineSupported(new Line.Info(TargetDataLine.class));
		} catch (RuntimeException e) {
			return false;
		}
	}
}
