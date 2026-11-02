package io.github.dylantopic.langmod.core.tts;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * The voices installed in Windows (Settings > Time &amp; language > Speech), used through Windows
 * PowerShell 5.1, which comes with Windows 10 and 11 and can call the WinRT speech API: the API that
 * sees every installed voice. The PowerShell process runs {@code windows-voices.ps1} (written next to
 * the config), is started on first use and stops after a few idle minutes.
 */
public final class WindowsTts implements TextToSpeech {
	private static final String SCRIPT = "windows-voices.ps1";
	private static final long START_TIMEOUT_SECONDS = 30;
	private static final long REQUEST_TIMEOUT_SECONDS = 20;
	private static final long IDLE_SECONDS = 300;

	private final Path powershell;
	private final Path directory;
	private final ScheduledExecutorService timers = Executors.newSingleThreadScheduledExecutor(runnable -> {
		Thread thread = new Thread(runnable, "LangMod voices timer");
		thread.setDaemon(true);
		return thread;
	});
	private final Deque<String> errorLines = new ArrayDeque<>();
	private volatile Process process;
	private BufferedReader output;
	private Writer input;
	private ScheduledFuture<?> idleStop;

	/** @param directory where the PowerShell script is written */
	public WindowsTts(Path powershell, Path directory) {
		this.powershell = powershell;
		this.directory = directory;
	}

	/** Windows PowerShell 5.1 (not PowerShell 7, which cannot load WinRT types), if this is Windows. */
	public static Optional<Path> findPowerShell() {
		if (!System.getProperty("os.name", "").startsWith("Windows")) {
			return Optional.empty();
		}

		String root = System.getenv("SystemRoot");
		Path path = Path.of(root == null || root.isBlank() ? "C:\\Windows" : root, "System32", "WindowsPowerShell", "v1.0", "powershell.exe");
		return Files.isRegularFile(path) ? Optional.of(path) : Optional.empty();
	}

	@Override
	public synchronized List<Voice> voices() throws IOException {
		stop(); // a fresh process, so voices installed meanwhile are seen
		List<Voice> voices = new ArrayList<>();

		for (String line : request("voices", "end")) {
			String[] parts = line.split(" ");

			if (parts.length >= 4 && parts[0].equals("voice")) {
				voices.add(new Voice(decode(parts[1]), decode(parts[2]), parts[3], parts.length > 4 ? parts[4] : ""));
			}
		}

		return voices;
	}

	@Override
	public synchronized byte[] synthesize(Voice voice, String text, double rate) throws IOException {
		String line = request("say " + encode(voice.id()) + " " + String.format(Locale.ROOT, "%.2f", rate) + " " + encode(text), null).getFirst();

		if (!line.startsWith("wav ")) {
			throw new IOException("Unexpected answer from Windows PowerShell: " + abbreviate(line));
		}

		return Base64.getDecoder().decode(line.substring(4));
	}

	@Override
	public String describe() {
		return "Windows voices";
	}

	@Override
	public synchronized void close() {
		stop();
		timers.shutdownNow();
	}

	/**
	 * Sends one request and returns its answer: one line, or the lines before {@code end}. An answer
	 * "error ..." becomes an IOException with its message; a process that stops answering is killed
	 * and started again by the next request.
	 */
	private List<String> request(String request, String end) throws IOException {
		start();

		if (idleStop != null) {
			idleStop.cancel(false);
		}

		ScheduledFuture<?> timeout = timers.schedule(this::kill, REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);

		try {
			input.write(request);
			input.write('\n');
			input.flush();
			List<String> lines = new ArrayList<>();

			while (true) {
				String line = output.readLine();

				if (line == null) {
					throw new IOException("Windows PowerShell stopped answering" + errorSummary());
				}

				if (line.startsWith("error ")) {
					throw new TtsError(decode(line.substring(6)));
				}

				if (end == null) {
					return List.of(line);
				}

				if (line.equals(end)) {
					return lines;
				}

				lines.add(line);
			}
		} catch (TtsError e) {
			throw new IOException(e.getMessage()); // the process is fine, only this request failed
		} catch (IOException e) {
			stop();
			throw e;
		} finally {
			timeout.cancel(false);
			idleStop = timers.schedule(this::kill, IDLE_SECONDS, TimeUnit.SECONDS);
		}
	}

	private void start() throws IOException {
		if (process != null && process.isAlive()) {
			return;
		}

		stop();
		Files.createDirectories(directory);
		Path script = directory.resolve(SCRIPT);

		try (InputStream in = WindowsTts.class.getResourceAsStream(SCRIPT)) {
			if (in == null) {
				throw new IOException(SCRIPT + " is missing from the mod");
			}

			Files.write(script, in.readAllBytes());
		}

		ProcessBuilder builder = new ProcessBuilder(powershell.toString(), "-NoLogo", "-NoProfile", "-NonInteractive",
				"-ExecutionPolicy", "Bypass", "-File", script.toAbsolutePath().toString());
		Process started = builder.start();
		process = started;
		input = new OutputStreamWriter(started.getOutputStream(), StandardCharsets.US_ASCII);
		output = new BufferedReader(new InputStreamReader(started.getInputStream(), StandardCharsets.US_ASCII));
		collectErrors(started);
		ScheduledFuture<?> timeout = timers.schedule(this::kill, START_TIMEOUT_SECONDS, TimeUnit.SECONDS);

		try {
			String first = output.readLine();

			if (!"ready".equals(first)) {
				String reason = first != null && first.startsWith("error ") ? ": " + decode(first.substring(6)) : errorSummary();
				stop();
				throw new IOException("Windows speech is not available" + reason);
			}
		} finally {
			timeout.cancel(false);
		}
	}

	/** Keeps the last lines PowerShell writes to stderr, for error messages. */
	private void collectErrors(Process started) {
		Thread thread = new Thread(() -> {
			try (BufferedReader errors = new BufferedReader(new InputStreamReader(started.getErrorStream(), StandardCharsets.UTF_8))) {
				for (String line = errors.readLine(); line != null; line = errors.readLine()) {
					synchronized (errorLines) {
						errorLines.addLast(line);

						while (errorLines.size() > 8) {
							errorLines.removeFirst();
						}
					}
				}
			} catch (IOException e) {
				// The process ended.
			}
		}, "LangMod voices stderr");
		thread.setDaemon(true);
		thread.start();
	}

	private String errorSummary() {
		synchronized (errorLines) {
			String text = String.join(" ", errorLines).trim();
			return text.isEmpty() ? "" : ": " + abbreviate(text);
		}
	}

	/** Ends the process without waiting for the lock (also used by the timers while a request waits). */
	private void kill() {
		Process running = process;

		if (running != null) {
			running.destroyForcibly();
		}
	}

	private void stop() {
		kill();
		process = null;
		output = null;
		input = null;
	}

	private static String encode(String text) {
		return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
	}

	private static String decode(String base64) {
		try {
			return new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);
		} catch (IllegalArgumentException e) {
			return base64;
		}
	}

	private static String abbreviate(String text) {
		return text.length() > 300 ? text.substring(0, 300) + "..." : text;
	}

	/** An "error ..." answer: the request failed but the process is fine. */
	private static final class TtsError extends IOException {
		private static final long serialVersionUID = 1L;

		TtsError(String message) {
			super(message);
		}
	}
}
