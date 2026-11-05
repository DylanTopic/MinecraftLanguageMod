package io.github.dylantopic.langmod.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import io.github.dylantopic.langmod.LangMod;

/** Client -> server: what the player said (or typed) to the villager they are talking to. */
public record VillagerSayPayload(int villagerId, String text) implements CustomPacketPayload {
	public static final int MAX_LENGTH = 256;
	public static final Type<VillagerSayPayload> TYPE = new Type<>(LangMod.id("villager_say"));
	public static final StreamCodec<RegistryFriendlyByteBuf, VillagerSayPayload> CODEC =
			CustomPacketPayload.codec(VillagerSayPayload::write, VillagerSayPayload::new);

	private VillagerSayPayload(RegistryFriendlyByteBuf buf) {
		this(buf.readVarInt(), buf.readUtf(MAX_LENGTH));
	}

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeVarInt(villagerId);
		buf.writeUtf(text.length() > MAX_LENGTH ? text.substring(0, MAX_LENGTH) : text, MAX_LENGTH);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
