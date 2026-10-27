package io.github.dylantopic.langmod.core.speech;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

/** Checks the HTTP protocol of {@link WhisperServerEngine} against a stand-in for whisper-server. */
class WhisperServerEngineTest {
	@Test
	void sendsAudioAndReadsTheTranscript() throws Exception {
		AtomicReference<String> body = new AtomicReference<>();
		HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/health", exchange -> reply(exchange, 200, "{\"status\":\"ok\"}"));
		server.createContext("/inference", exchange -> {
			body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1));
			reply(exchange, 200, "{\"text\":\" 蘋果。\"}");
		});
		server.start();

		try (WhisperServerEngine engine = new WhisperServerEngine(null, Path.of("ggml-small.bin"),
				server.getAddress().getPort(), 4, true, Path.of("unused.log"), line -> { })) {
			// No executable configured: it must connect to the server that is already running.
			engine.load();
			String text = engine.transcribe(new float[1600], new SpeechEngine.Request("zh", "苹果"));
			assertEquals("蘋果。", text);
		} finally {
			server.stop(0);
		}

		String request = body.get();
		assertTrue(request.contains("name=\"language\"\r\n\r\nzh\r\n"), request);
		assertTrue(request.contains("name=\"response_format\"\r\n\r\njson\r\n"));
		assertTrue(request.contains("name=\"prompt\"\r\n\r\n" + new String("苹果".getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1)));
		assertTrue(request.contains("filename=\"clip.wav\"") && request.contains("RIFF") && request.contains("WAVE"));
	}

	@Test
	void reportsServerErrors() throws Exception {
		HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/health", exchange -> reply(exchange, 200, "{\"status\":\"ok\"}"));
		server.createContext("/inference", exchange -> reply(exchange, 500, "{\"error\":\"boom\"}"));
		server.start();

		try (WhisperServerEngine engine = new WhisperServerEngine(null, Path.of("m.bin"), server.getAddress().getPort(), 1, false,
				Path.of("unused.log"), line -> { })) {
			engine.load();
			IOException e = assertThrows(IOException.class, () -> engine.transcribe(new float[160], new SpeechEngine.Request("zh", null)));
			assertTrue(e.getMessage().contains("500"));
		} finally {
			server.stop(0);
		}
	}

	@Test
	void failsClearlyWithoutServerOrExecutable() {
		try (WhisperServerEngine engine = new WhisperServerEngine(null, Path.of("m.bin"), 1, 1, false, Path.of("unused.log"), line -> { })) {
			IOException e = assertThrows(IOException.class, engine::load);
			assertTrue(e.getMessage().contains("whisperServerPath"), e.getMessage());
		}
	}

	private static void reply(HttpExchange exchange, int status, String json) throws IOException {
		byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().add("Content-Type", "application/json");
		exchange.sendResponseHeaders(status, bytes.length);

		try (OutputStream out = exchange.getResponseBody()) {
			out.write(bytes);
		}
	}
}
