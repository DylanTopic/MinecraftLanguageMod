package io.github.dylantopic.langmod.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import io.github.dylantopic.langmod.LangMod;

/**
 * Server -> client: periodic timer sync for the active challenge (twice a second).
 *
 * @param timerRunning false while the countdown is paused (player speaking, Whisper busy)
 */
public record ChallengeStatePayload(int challengeId, int remainingTicks, boolean timerRunning, int attemptsUsed, int queued)
		implements CustomPacketPayload {
	public static final Type<ChallengeStatePayload> TYPE = new Type<>(LangMod.id("challenge_state"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ChallengeStatePayload> CODEC =
			CustomPacketPayload.codec(ChallengeStatePayload::write, ChallengeStatePayload::new);

	private ChallengeStatePayload(RegistryFriendlyByteBuf buf) {
		this(buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readVarInt(), buf.readVarInt());
	}

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeVarInt(challengeId);
		buf.writeVarInt(remainingTicks);
		buf.writeBoolean(timerRunning);
		buf.writeVarInt(attemptsUsed);
		buf.writeVarInt(queued);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
