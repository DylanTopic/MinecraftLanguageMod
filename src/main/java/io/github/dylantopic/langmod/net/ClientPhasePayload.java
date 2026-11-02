package io.github.dylantopic.langmod.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import io.github.dylantopic.langmod.LangMod;

/**
 * Client -> server: what the microphone side is doing. The countdown pauses while the player is
 * speaking, while Whisper is busy (so slow transcriptions never cost the player the item) and while
 * the name is being said to the player after a mistake.
 */
public record ClientPhasePayload(int challengeId, Phase phase) implements CustomPacketPayload {
	public static final Type<ClientPhasePayload> TYPE = new Type<>(LangMod.id("client_phase"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ClientPhasePayload> CODEC =
			CustomPacketPayload.codec(ClientPhasePayload::write, ClientPhasePayload::new);

	public enum Phase {
		/** Waiting for the player to speak (countdown running). */
		LISTENING,
		/** The player is speaking. */
		RECORDING,
		/** Whisper is transcribing. */
		CHECKING,
		/** The name is being said out loud (the microphone is paused). */
		HEARING
	}

	private ClientPhasePayload(RegistryFriendlyByteBuf buf) {
		this(buf.readVarInt(), Phase.values()[buf.readVarInt()]);
	}

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeVarInt(challengeId);
		buf.writeVarInt(phase.ordinal());
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
