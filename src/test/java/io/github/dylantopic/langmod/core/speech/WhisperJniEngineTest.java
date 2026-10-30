package io.github.dylantopic.langmod.core.speech;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WhisperJniEngineTest {
	@Test
	void extractsTheSelfContainedWindowsLibraryOnce(@TempDir Path dir) throws Exception {
		Path dll = WhisperJniEngine.extractWindowsLibrary(dir.resolve("natives"));
		assertEquals("whisper-jni.dll", dll.getFileName().toString());
		assertTrue(Files.size(dll) > 500_000, "the full build (whisper.cpp linked in), not the 50 KB stub");

		// A second start finds the same file and leaves it alone (another running game may have it loaded).
		FileTime old = FileTime.fromMillis(0);
		Files.setLastModifiedTime(dll, old);
		assertEquals(dll, WhisperJniEngine.extractWindowsLibrary(dir.resolve("natives")));
		assertEquals(old, Files.getLastModifiedTime(dll));

		// A damaged copy is replaced.
		Files.write(dll, new byte[] {1, 2, 3});
		WhisperJniEngine.extractWindowsLibrary(dir.resolve("natives"));
		assertTrue(Files.size(dll) > 500_000);
		try (var files = Files.list(dll.getParent())) {
			assertEquals(1, files.count(), "no temporary files left behind");
		}
	}
}
