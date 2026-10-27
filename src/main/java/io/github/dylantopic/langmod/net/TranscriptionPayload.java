package io.github.dylantopic.langmod.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import io.github.dylantopic.langmod.LangMod;

/**
 * Client -> server: what Whisper heard for the current challenge. The server does the matching.
 *
 * @param ok false if transcription failed or timed out (never counts as an attempt)
 * @param text the raw transcription, or the error message if {@code ok} is false
 * @param millis how long Whisper took
 */
public record TranscriptionPayload(int challengeId, boolean ok, String text, int millis) implements CustomPacketPayload {
	public static final Type<TranscriptionPayload> TYPE = new Type<>(LangMod.id("transcription"));
	public static final StreamCodec<RegistryFriendlyByteBuf, TranscriptionPayload> CODEC =
			CustomPacketPayload.codec(TranscriptionPayload::write, TranscriptionPayload::new);

	private TranscriptionPayload(RegistryFriendlyByteBuf buf) {
		this(buf.readVarInt(), buf.readBoolean(), buf.readUtf(1024), buf.readVarInt());
	}

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeVarInt(challengeId);
		buf.writeBoolean(ok);
		buf.writeUtf(text.length() > 1024 ? text.substring(0, 1024) : text, 1024);
		buf.writeVarInt(millis);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
