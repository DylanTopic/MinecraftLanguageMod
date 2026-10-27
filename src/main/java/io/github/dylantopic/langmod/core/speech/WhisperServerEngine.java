package io.github.dylantopic.langmod.core.speech;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.github.dylantopic.langmod.core.audio.Wav;

/**
 * Talks to whisper.cpp's {@code whisper-server} on 127.0.0.1. If {@code executable} is set, the
 * server is started (and stopped) by the mod; otherwise an already running server is used.
 *
 * <p>This is the way to get GPU acceleration and the newest whisper.cpp: download a GPU build of
 * whisper.cpp (e.g. the CUDA build for NVIDIA cards) and point the config at its whisper-server.
 * A crash of the server cannot take Minecraft down with it.
 */
public final class WhisperServerEngine implements SpeechEngine {
	private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(120);

	private final Path executable;
	private final Path model;
	private final int port;
	private final int threads;
	private final boolean useGpu;
	private final Path logFile;
	private final Consumer<String> log;
	private final HttpClient http = HttpClient.newBuilder()
			.proxy(ProxySelector.of(null))
			.connectTimeout(Duration.ofSeconds(2))
			.build();
	private Process process;

	/**
	 * @param executable whisper-server(.exe) to start, or null to connect to a running server
	 * @param logFile where the server's output goes
	 */
	public WhisperServerEngine(Path executable, Path model, int port, int threads, boolean useGpu, Path logFile, Consumer<String> log) {
		this.executable = executable;
		this.model = model;
		this.port = port;
		this.threads = threads;
		this.useGpu = useGpu;
		this.logFile = logFile;
		this.log = log;
	}

	@Override
	public void load() throws IOException, InterruptedException {
		if (isHealthy()) {
			log.accept("Using the whisper-server already running on port " + port);
			return;
		}

		if (executable == null) {
			throw new IOException("No whisper-server is running on port " + port + " and no whisperServerPath is configured");
		}

		if (!Files.isRegularFile(executable)) {
			throw new IOException("whisper-server not found at " + executable);
		}

		if (!Files.isRegularFile(model)) {
			throw new IOException("Model file not found: " + model);
		}

		List<String> command = new ArrayList<>(List.of(executable.toString(), "-m", model.toString(), "-l", "zh",
				"-t", Integer.toString(threads), "--host", "127.0.0.1", "--port", Integer.toString(port)));

		if (!useGpu) {
			command.add("-ng");
		}

		Files.createDirectories(logFile.getParent());
		log.accept("Starting " + String.join(" ", command));
		process = new ProcessBuilder(command)
				.directory(executable.getParent().toFile())
				.redirectErrorStream(true)
				.redirectOutput(logFile.toFile())
				.start();

		long deadline = System.nanoTime() + STARTUP_TIMEOUT.toNanos();

		while (System.nanoTime() < deadline) {
			if (!process.isAlive()) {
				throw new IOException("whisper-server exited with code " + process.exitValue() + "; see " + logFile);
			}

			if (isHealthy()) {
				return;
			}

			Thread.sleep(250);
		}

		close();
		throw new IOException("whisper-server did not become ready within " + STARTUP_TIMEOUT.toSeconds() + "s; see " + logFile);
	}

	@Override
	public String transcribe(float[] samples, Request request) throws IOException, InterruptedException {
		String boundary = "langmod-" + UUID.randomUUID();
		ByteArrayOutputStream body = new ByteArrayOutputStream();
		field(body, boundary, "language", request.language());
		field(body, boundary, "response_format", "json");
		field(body, boundary, "temperature", "0.0");
		field(body, boundary, "no_timestamps", "true");
		field(body, boundary, "suppress_nst", "true");

		if (request.initialPrompt() != null) {
			field(body, boundary, "prompt", request.initialPrompt());
		}

		body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"clip.wav\"\r\n"
				+ "Content-Type: audio/wav\r\n\r\n").getBytes(StandardCharsets.UTF_8));
		body.write(Wav.encode(samples, 16_000));
		body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

		HttpRequest httpRequest = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/inference"))
				.timeout(Duration.ofSeconds(60))
				.header("Content-Type", "multipart/form-data; boundary=" + boundary)
				.POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
				.build();
		HttpResponse<String> response = http.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

		if (response.statusCode() != 200) {
			throw new IOException("whisper-server answered HTTP " + response.statusCode() + ": " + response.body());
		}

		JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();

		if (json.has("error")) {
			throw new IOException("whisper-server error: " + json.get("error").getAsString());
		}

		return json.get("text").getAsString().trim();
	}

	@Override
	public String describe() {
		return "whisper-server on port " + port + " (" + model.getFileName() + ", " + threads + " threads, GPU " + (useGpu ? "allowed" : "off") + ")";
	}

	@Override
	public void close() {
		if (process != null) {
			process.descendants().forEach(ProcessHandle::destroy);
			process.destroy();
			process = null;
		}
	}

	private boolean isHealthy() {
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/health"))
					.timeout(Duration.ofSeconds(2))
					.GET()
					.build();
			HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			return response.statusCode() == 200 && response.body().contains("\"ok\"");
		} catch (IOException e) {
			return false;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return false;
		}
	}

	private static void field(ByteArrayOutputStream body, String boundary, String name, String value) throws IOException {
		body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"\r\n\r\n" + value + "\r\n")
				.getBytes(StandardCharsets.UTF_8));
	}
}
