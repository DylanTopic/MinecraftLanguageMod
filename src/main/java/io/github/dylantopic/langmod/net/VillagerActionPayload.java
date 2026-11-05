package io.github.dylantopic.langmod.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import io.github.dylantopic.langmod.LangMod;

/** Client -> server: what the player chose to do with a villager. */
public record VillagerActionPayload(int villagerId, Action action) implements CustomPacketPayload {
	public static final Type<VillagerActionPayload> TYPE = new Type<>(LangMod.id("villager_action"));
	public static final StreamCodec<RegistryFriendlyByteBuf, VillagerActionPayload> CODEC =
			CustomPacketPayload.codec(VillagerActionPayload::write, VillagerActionPayload::new);

	public enum Action {
		/** Open the normal trading screen. */
		TRADE,
		/** Start a conversation (the villager stands still and looks at the player). */
		TALK,
		/** The conversation screen was closed. */
		STOP
	}

	private VillagerActionPayload(RegistryFriendlyByteBuf buf) {
		this(buf.readVarInt(), Action.values()[Math.min(buf.readVarInt(), Action.values().length - 1)]);
	}

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeVarInt(villagerId);
		buf.writeVarInt(action.ordinal());
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
