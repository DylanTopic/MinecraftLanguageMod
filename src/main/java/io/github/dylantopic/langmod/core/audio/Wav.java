package io.github.dylantopic.langmod.core.audio;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Writes float samples as a 16-bit PCM WAV file (sent to whisper-server) and reads the WAV files
 * the computer's voices make.
 */
public final class Wav {
	private static final int RIFF = 0x46464952;
	private static final int WAVE = 0x45564157;
	private static final int FMT = 0x20746D66;
	private static final int DATA = 0x61746164;

	/**
	 * The sound in a WAV file.
	 *
	 * @param floating 32-bit float samples rather than signed integers (unsigned for 8-bit)
	 * @param data the samples as stored in the file, little-endian, channels interleaved
	 */
	public record Audio(int sampleRate, int channels, int bitsPerSample, boolean floating, byte[] data) {
		public long millis() {
			int frameBytes = channels * (bitsPerSample / 8);
			return frameBytes <= 0 || sampleRate <= 0 ? 0 : (long) (data.length / frameBytes) * 1000 / sampleRate;
		}
	}

	private Wav() {
	}

	/** Reads a RIFF/WAVE file with PCM samples (plain, float or WAVE_FORMAT_EXTENSIBLE). */
	public static Audio decode(byte[] file) {
		ByteBuffer buffer = ByteBuffer.wrap(file).order(ByteOrder.LITTLE_ENDIAN);

		if (file.length < 12 || buffer.getInt(0) != RIFF || buffer.getInt(8) != WAVE) {
			throw new IllegalArgumentException("not a WAV file");
		}

		int format = -1;
		int channels = 0;
		int sampleRate = 0;
		int bits = 0;
		int position = 12;

		while (position + 8 <= file.length) {
			int id = buffer.getInt(position);
			int size = buffer.getInt(position + 4);
			int body = position + 8;
			// A file written while streaming may not know its length: take what is there.
			int length = size < 0 || size > file.length - body ? file.length - body : size;

			if (id == FMT && length >= 16) {
				format = buffer.getShort(body) & 0xFFFF;
				channels = buffer.getShort(body + 2);
				sampleRate = buffer.getInt(body + 4);
				bits = buffer.getShort(body + 14);

				if (format == 0xFFFE && length >= 26) {
					format = buffer.getShort(body + 24) & 0xFFFF; // WAVE_FORMAT_EXTENSIBLE: the sub-format starts with the format code
				}
			} else if (id == DATA) {
				if (format != 1 && format != 3) {
					throw new IllegalArgumentException(format < 0 ? "WAV file without a format" : "unsupported WAV format " + format);
				}

				if (channels <= 0 || sampleRate <= 0 || bits % 8 != 0 || bits <= 0) {
					throw new IllegalArgumentException("bad WAV format: " + channels + " channels, " + sampleRate + " Hz, " + bits + " bits");
				}

				byte[] data = new byte[length];
				System.arraycopy(file, body, data, 0, length);
				return new Audio(sampleRate, channels, bits, format == 3, data);
			}

			position = body + length + (length & 1);
		}

		throw new IllegalArgumentException("WAV file without sound");
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
