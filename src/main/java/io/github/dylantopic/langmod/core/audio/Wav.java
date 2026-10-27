package io.github.dylantopic.langmod.core.audio;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Encodes float samples as a 16-bit PCM WAV file (used to send audio to whisper-server). */
public final class Wav {
	private Wav() {
	}

	public static byte[] encode(float[] samples, int sampleRate) {
		int dataBytes = samples.length * 2;
		ByteBuffer buffer = ByteBuffer.allocate(44 + dataBytes).order(ByteOrder.LITTLE_ENDIAN);
		buffer.put(new byte[] {'R', 'I', 'F', 'F'}).putInt(36 + dataBytes).put(new byte[] {'W', 'A', 'V', 'E'});
		buffer.put(new byte[] {'f', 'm', 't', ' '}).putInt(16).putShort((short) 1).putShort((short) 1)
				.putInt(sampleRate).putInt(sampleRate * 2).putShort((short) 2).putShort((short) 16);
		buffer.put(new byte[] {'d', 'a', 't', 'a'}).putInt(dataBytes);

		for (float sample : samples) {
			float clamped = Math.max(-1f, Math.min(1f, sample));
			buffer.putShort((short) Math.round(clamped * 32767));
		}

		return buffer.array();
	}
}
