package io.github.dylantopic.langmod.core.speech;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class WhisperModelsTest {
	@Test
	void audioContextFitsTheClip() {
		assertEquals(0, WhisperModels.audioContext(16_000, 0), "0 keeps Whisper's full window");
		assertEquals(512, WhisperModels.audioContext(16_000, 512));
		assertEquals(1500, WhisperModels.audioContext(16_000, 4000), "never more than 30 s");
		// Automatic: 50 frames per second of audio plus a 64 frame margin, at least 128.
		assertEquals(128, WhisperModels.audioContext(8_000, -1));
		assertEquals(50 * 5 + 64, WhisperModels.audioContext(5 * 16_000, -1));
		assertEquals(101 + 64, WhisperModels.audioContext(32_001, -1), "a partial frame counts");
		assertEquals(1500, WhisperModels.audioContext(60 * 16_000, -1));
	}

	@Test
	void fileNames() {
		assertEquals("ggml-small.bin", WhisperModels.fileName("small"));
		assertEquals("https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin", WhisperModels.downloadUrl("base"));
	}
}
