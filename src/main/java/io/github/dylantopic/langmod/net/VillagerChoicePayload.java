package io.github.dylantopic.langmod.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import io.github.dylantopic.langmod.LangMod;

/**
 * Server -> client: the player right-clicked a villager; ask whether to trade or to talk.
 *
 * @param villagerId the villager's entity id
 * @param canTrade false for a child or a villager with nothing to trade
 */
public record VillagerChoicePayload(int villagerId, boolean canTrade) implements CustomPacketPayload {
	public static final Type<VillagerChoicePayload> TYPE = new Type<>(LangMod.id("villager_choice"));
	public static final StreamCodec<RegistryFriendlyByteBuf, VillagerChoicePayload> CODEC =
			CustomPacketPayload.codec(VillagerChoicePayload::write, VillagerChoicePayload::new);

	private VillagerChoicePayload(RegistryFriendlyByteBuf buf) {
		this(buf.readVarInt(), buf.readBoolean());
	}

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeVarInt(villagerId);
		buf.writeBoolean(canTrade);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
