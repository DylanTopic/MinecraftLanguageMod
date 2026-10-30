package io.github.dylantopic.langmod.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import io.github.dylantopic.langmod.LangMod;

/** Client -> server: the Items screen asks which items the player has learned in a language. */
public record LearnedItemsRequestPayload(String language) implements CustomPacketPayload {
	public static final Type<LearnedItemsRequestPayload> TYPE = new Type<>(LangMod.id("learned_items_request"));
	public static final StreamCodec<RegistryFriendlyByteBuf, LearnedItemsRequestPayload> CODEC =
			CustomPacketPayload.codec(LearnedItemsRequestPayload::write, LearnedItemsRequestPayload::new);

	private LearnedItemsRequestPayload(RegistryFriendlyByteBuf buf) {
		this(buf.readUtf());
	}

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeUtf(language);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
