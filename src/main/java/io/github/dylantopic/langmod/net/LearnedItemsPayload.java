package io.github.dylantopic.langmod.net;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import io.github.dylantopic.langmod.LangMod;

/** Server -> client: the items the player has learned in a language (ids such as {@code minecraft:apple}). */
public record LearnedItemsPayload(String language, List<String> itemIds) implements CustomPacketPayload {
	public static final Type<LearnedItemsPayload> TYPE = new Type<>(LangMod.id("learned_items"));
	public static final StreamCodec<RegistryFriendlyByteBuf, LearnedItemsPayload> CODEC =
			CustomPacketPayload.codec(LearnedItemsPayload::write, LearnedItemsPayload::new);

	private LearnedItemsPayload(RegistryFriendlyByteBuf buf) {
		this(buf.readUtf(), readIds(buf));
	}

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeUtf(language);
		buf.writeVarInt(itemIds.size());

		for (String id : itemIds) {
			buf.writeUtf(id);
		}
	}

	private static List<String> readIds(RegistryFriendlyByteBuf buf) {
		int count = Math.min(buf.readVarInt(), 8192);
		List<String> ids = new ArrayList<>(count);

		for (int i = 0; i < count; i++) {
			ids.add(buf.readUtf());
		}

		return ids;
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
