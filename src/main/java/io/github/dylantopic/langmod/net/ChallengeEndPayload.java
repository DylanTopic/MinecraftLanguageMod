package io.github.dylantopic.langmod.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import io.github.dylantopic.langmod.LangMod;

/** Server -> client: the challenge is over. Closes the mic and plays the end animation. */
public record ChallengeEndPayload(int challengeId, Outcome outcome, String detail) implements CustomPacketPayload {
	public static final Type<ChallengeEndPayload> TYPE = new Type<>(LangMod.id("challenge_end"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ChallengeEndPayload> CODEC =
			CustomPacketPayload.codec(ChallengeEndPayload::write, ChallengeEndPayload::new);

	public enum Outcome {
		/** Said correctly: item given and learned. */
		SUCCESS,
		/** Out of attempts or time: the item disappeared. */
		FAILED,
		/** Death, quitting, the mod being switched off or speech becoming unavailable: item given back, nothing counted. */
		CANCELLED
	}

	private ChallengeEndPayload(RegistryFriendlyByteBuf buf) {
		this(buf.readVarInt(), Outcome.values()[buf.readVarInt()], buf.readUtf());
	}

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeVarInt(challengeId);
		buf.writeVarInt(outcome.ordinal());
		buf.writeUtf(detail);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
