package io.github.dylantopic.langmod.client.voice;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.LineUnavailableException;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.sounds.SoundSource;

import io.github.dylantopic.langmod.LangMod;
import io.github.dylantopic.langmod.LangModConfig;
import io.github.dylantopic.langmod.LanguagePacks;
import io.github.dylantopic.langmod.client.ClientMessages;
import io.github.dylantopic.langmod.core.audio.Wav;
import io.github.dylantopic.langmod.core.lang.Language;
import io.github.dylantopic.langmod.core.lang.NameEntry;
import io.github.dylantopic.langmod.core.tts.TextToSpeech;
import io.github.dylantopic.langmod.core.tts.Voice;
import io.github.dylantopic.langmod.core.tts.VoiceChooser;
import io.github.dylantopic.langmod.core.tts.WindowsTts;

/**
 * Says item names out loud with a voice installed on the computer: after a wrong answer, when an
 * item is lost, on the "hear it again" key and as a preview in the settings. The sound is made and
 * played on a background thread at Minecraft's Voice/Speech volume. {@link #busy()} stays true from
 * the request until shortly after the voice stops; meanwhile the microphone is paused (so it does
 * not hear the voice) and the countdown waits.
 */
public final class Pronouncer {
	private static final Pronouncer INSTANCE = new Pronouncer();
	/** After the voice stops, the speakers' delay and the room can still carry it into the microphone. */
	private static final long ECHO_MILLIS = 600;
	private static final int CACHE_SIZE = 64;

	public enum Status {
		/** Not asked for yet. */
		IDLE,
		/** Looking for the installed voices. */
		STARTING,
		READY,
		/** Not Windows, or Windows speech failed: see {@link #problem()}. */
		UNAVAILABLE
	}

	private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "LangMod voice");
		thread.setDaemon(true);
		return thread;
	});
	private final Map<String, byte[]> cache = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
		private static final long serialVersionUID = 1L;

		@Override
		protected boolean removeEldestEntry(Map.Entry<String, byte[]> eldest) {
			return size() > CACHE_SIZE;
		}
	});
	private final AtomicInteger waiting = new AtomicInteger();
	private final AtomicInteger said = new AtomicInteger();
	/** Languages whose missing voice was already pointed out this session (render thread). */
	private final Set<Language> hinted = EnumSet.noneOf(Language.class);
	private volatile Status status = Status.IDLE;
	private volatile String problem = "";
	private volatile List<Voice> voices = List.of();
	private volatile long busyUntil = System.nanoTime();
	private volatile String lastSaid = "";
	private boolean unavailableHinted;
	// Worker thread only:
	private TextToSpeech engine;
	private TextToSpeech testEngine;
	private boolean silent;
	private Clip clip;
	private boolean warnedNoSound;

	private Pronouncer() {
	}

	public static Pronouncer get() {
		return INSTANCE;
	}

	/** Looks for the installed voices in the background, the first time it is called. */
	public void start() {
		if (status == Status.IDLE) {
			reload();
		}
	}

	/** Looks for the installed voices again (to see one added in Windows meanwhile). */
	public CompletableFuture<List<Voice>> reload() {
		if (worker.isShutdown()) {
			return CompletableFuture.completedFuture(voices);
		}

		status = Status.STARTING;
		return CompletableFuture.supplyAsync(this::loadVoices, worker);
	}

	public Status status() {
		return status;
	}

	/** Why voices are unavailable, if they are. */
	public String problem() {
		return problem;
	}

	/** The installed voices (empty until found). */
	public List<Voice> voices() {
		return voices;
	}

	/** The voice that says the language's names: the one chosen in the settings, or the best installed one. */
	public Optional<Voice> voiceFor(Language language) {
		return VoiceChooser.choose(language, voices, LangModConfig.get().voiceName(language));
	}

	/** True while a name is being prepared for playing, played, and for a moment after. */
	public boolean busy() {
		return waiting.get() > 0 || System.nanoTime() - busyUntil < 0;
	}

	/** Makes the name's sound ahead of time, so it plays at once when needed. */
	public void prepare(Language language, NameEntry entry) {
		start();
		String text = spokenText(language, entry);
		LangModConfig config = LangModConfig.get();
		double speed = config.voiceSpeed;
		String preferred = config.voiceName(language);
		run(() -> {
			try {
				sound(language, text, speed, preferred);
			} catch (IOException | RuntimeException e) {
				// Reported if it is played.
			}
		});
	}

	/** Says the name of an item. */
	public void say(Language language, NameEntry entry) {
		sayText(language, spokenText(language, entry));
	}

	/** Says some text with the language's voice; if none is installed, says how to add one (once per language). */
	public void sayText(Language language, String text) {
		start();

		if (status == Status.UNAVAILABLE) {
			if (!unavailableHinted) {
				unavailableHinted = true;
				ClientMessages.info("Names can't be said out loud: " + problem + " (turn \"Say the name when wrong\" off in the settings to hide this).");
			}

			return;
		}

		if (status == Status.READY && voiceFor(language).isEmpty()) {
			hintMissingVoice(language);
			return;
		}

		LangModConfig config = LangModConfig.get();
		double speed = config.voiceSpeed;
		String preferred = config.voiceName(language);
		float volume = volume();
		waiting.incrementAndGet();
		boolean queued = run(() -> {
			try {
				play(sound(language, text, speed, preferred), volume);
				lastSaid = text;
				said.incrementAndGet();
			} catch (NoVoice e) {
				Minecraft.getInstance().execute(() -> hintMissingVoice(language));
			} catch (IOException | RuntimeException e) {
				LangMod.LOGGER.warn("Could not say '{}': {}", text, e.getMessage());
			} finally {
				waiting.decrementAndGet();
			}
		});

		if (!queued) {
			waiting.decrementAndGet();
		}
	}

	/** Says the language's word for apple (the preview in the settings). */
	public void preview(Language language) {
		try {
			LanguagePacks.get(language).name("item.minecraft.apple").ifPresent(apple -> say(language, apple));
		} catch (RuntimeException e) {
			LangMod.LOGGER.warn("No preview for {}: {}", language.id(), e.getMessage());
		}
	}

	/** Stops the voice (leaving a world). */
	public void stop() {
		busyUntil = System.nanoTime();
		run(this::stopClip);
	}

	public void shutdown() {
		run(() -> {
			stopClip();

			if (engine != null) {
				engine.close();
			}
		});
		worker.shutdown();
	}

	/** Queues work for the voice thread; false once the game is closing. */
	private boolean run(Runnable task) {
		try {
			worker.execute(task);
			return true;
		} catch (RejectedExecutionException e) {
			return false;
		}
	}

	/** Points out, once per language and session, that a voice is missing. */
	private void hintMissingVoice(Language language) {
		if (hinted.add(language)) {
			ClientMessages.info(missingVoiceMessage(language));
		}
	}

	/** "To hear Korean names said out loud, add a Korean voice in Windows: ..." */
	public static String missingVoiceMessage(Language language) {
		return "To hear " + language.englishName() + " names said out loud, add a " + voiceLanguage(language)
				+ " voice in Windows: Settings > Time & language > Speech > Add voices, then run /langmod voices.";
	}

	/** The language a borrowed voice speaks: Latin is read by an Italian voice, Bosnian by a Croatian one. */
	public static String voiceLanguage(Language language) {
		return switch (language) {
			case LATIN -> "Italian";
			case BOSNIAN -> "Croatian";
			default -> language.englishName();
		};
	}

	/** Game tests: use this engine instead of the Windows voices, and make no sound. */
	public void useEngineForTesting(TextToSpeech testing) {
		worker.execute(() -> {
			testEngine = testing;
			engine = null;
			silent = true;
		});
		reload();
	}

	/** The text said last (for the game tests). */
	public String lastSaid() {
		return lastSaid;
	}

	/** How many times something was said (for the game tests). */
	public int saidCount() {
		return said.get();
	}

	private List<Voice> loadVoices() {
		try {
			if (engine == null) {
				engine = createEngine();
			}

			List<Voice> found = List.copyOf(engine.voices());
			voices = found;
			cache.clear();
			problem = "";
			status = Status.READY;
			LangMod.LOGGER.info("{}: {}", engine.describe(), found.isEmpty() ? "none installed"
					: found.stream().map(Voice::describe).collect(Collectors.joining(", ")));
		} catch (IOException | RuntimeException e) {
			voices = List.of();
			problem = e.getMessage() == null ? e.toString() : e.getMessage();
			status = Status.UNAVAILABLE;
			LangMod.LOGGER.warn("Item names can't be said out loud: {}", problem);
		}

		return voices;
	}

	private TextToSpeech createEngine() throws IOException {
		if (testEngine != null) {
			return testEngine;
		}

		Path powershell = WindowsTts.findPowerShell().orElseThrow(() -> new IOException("it uses the voices of Windows, and this is not Windows"));
		return new WindowsTts(powershell, LangModConfig.directory());
	}

	/** The name as a WAV file, made once and then kept for a while. */
	private byte[] sound(Language language, String text, double speed, String preferred) throws IOException {
		Voice voice = VoiceChooser.choose(language, voices, preferred).orElseThrow(NoVoice::new);
		String key = voice.id() + '\n' + speed + '\n' + text;
		byte[] wav = cache.get(key);

		if (wav == null) {
			wav = engine.synthesize(voice, text, speed);
			cache.put(key, wav);
		}

		return wav;
	}

	private void play(byte[] wav, float volume) {
		Wav.Audio audio = Wav.decode(wav);
		stopClip();

		if (volume <= 0 && !silent) {
			return; // Voice/Speech is turned all the way down: nothing to wait for
		}

		busyUntil = System.nanoTime() + (audio.millis() + ECHO_MILLIS) * 1_000_000L;

		if (silent) {
			return;
		}

		try {
			AudioFormat.Encoding encoding = audio.floating() ? AudioFormat.Encoding.PCM_FLOAT
					: audio.bitsPerSample() == 8 ? AudioFormat.Encoding.PCM_UNSIGNED : AudioFormat.Encoding.PCM_SIGNED;
			int frameSize = audio.channels() * audio.bitsPerSample() / 8;
			AudioFormat format = new AudioFormat(encoding, audio.sampleRate(), audio.bitsPerSample(), audio.channels(), frameSize,
					audio.sampleRate(), false);
			byte[] data = audio.bitsPerSample() == 16 && !audio.floating() ? scaled(audio.data(), volume) : audio.data();
			Clip opened = AudioSystem.getClip();
			opened.open(format, data, 0, data.length);
			opened.addLineListener(event -> {
				if (event.getType() == LineEvent.Type.STOP) {
					event.getLine().close();
				}
			});
			opened.start();
			clip = opened;
		} catch (LineUnavailableException | IllegalArgumentException | SecurityException e) {
			if (!warnedNoSound) {
				warnedNoSound = true;
				LangMod.LOGGER.warn("Can't play the voice: {}", e.toString());
			}
		}
	}

	private void stopClip() {
		if (clip != null) {
			clip.stop();
			clip.close();
			clip = null;
		}
	}

	/** 16-bit little-endian samples at a lower volume. */
	private static byte[] scaled(byte[] data, float volume) {
		if (volume >= 0.999f) {
			return data;
		}

		byte[] out = new byte[data.length];

		for (int i = 0; i + 1 < data.length; i += 2) {
			int sample = Math.round((short) ((data[i] & 0xFF) | (data[i + 1] << 8)) * volume);
			out[i] = (byte) sample;
			out[i + 1] = (byte) (sample >> 8);
		}

		return out;
	}

	/** Minecraft's Master times Voice/Speech volume. */
	private static float volume() {
		Options options = Minecraft.getInstance().options;
		double master = options.getSoundSourceOptionInstance(SoundSource.MASTER).get();
		double voice = options.getSoundSourceOptionInstance(SoundSource.VOICE).get();
		return (float) Math.max(0, Math.min(1, master * voice));
	}

	/** No voice can say the language. */
	private static final class NoVoice extends IOException {
		private static final long serialVersionUID = 1L;
	}

	private static String spokenText(Language language, NameEntry entry) {
		try {
			return LanguagePacks.get(language).spokenText(entry);
		} catch (RuntimeException e) {
			return entry.text();
		}
	}
}
