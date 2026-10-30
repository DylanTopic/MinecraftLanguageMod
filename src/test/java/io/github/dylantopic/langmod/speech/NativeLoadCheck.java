package io.github.dylantopic.langmod.speech;

import io.github.dylantopic.langmod.core.speech.WhisperJniEngine;
import io.github.givimad.whisperjni.WhisperJNI;

/**
 * Loads the whisper.cpp native library the way the mod does, or with {@code --plain} the way
 * whisper-jni does on its own, and says whether it worked. Used by .github/workflows/speech.yml to
 * check that a malformed PATH entry on Windows no longer stops speech recognition from starting.
 */
public final class NativeLoadCheck {
	private NativeLoadCheck() {
	}

	public static void main(String[] args) throws Exception {
		if (args.length > 0 && args[0].equals("--plain")) {
			WhisperJNI.loadLibrary(line -> { });
		} else {
			WhisperJniEngine.loadNativeLibrary(line -> { });
		}

		System.out.println("native library loaded");
	}
}
