package io.github.dylantopic.langmod.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import io.github.dylantopic.langmod.LangMod;
import io.github.dylantopic.langmod.core.talk.Phrase;
import io.github.dylantopic.langmod.core.talk.TalkReply;

/**
 * Server -> client: the villager's answer, or that the conversation is over because the villager
 * walked away, fell asleep or is gone.
 *
 * @param translation the answer in English, or null if the conversation is in English
 * @param suggestion something the player could say next, or null
 */
public record VillagerReplyPayload(int villagerId, Kind kind, String text, String translation, Phrase suggestion) implements CustomPacketPayload {
	public static final Type<VillagerReplyPayload> TYPE = new Type<>(LangMod.id("villager_reply"));
	public static final StreamCodec<RegistryFriendlyByteBuf, VillagerReplyPayload> CODEC =
			CustomPacketPayload.codec(VillagerReplyPayload::write, VillagerReplyPayload::new);

	public enum Kind {
		REPLY,
		/** The conversation is over. */
		LEFT
	}

	public static VillagerReplyPayload reply(int villagerId, TalkReply reply) {
		return new VillagerReplyPayload(villagerId, Kind.REPLY, reply.text(), reply.translation(), reply.suggestion());
	}

	public static VillagerReplyPayload left(int villagerId) {
		return new VillagerReplyPayload(villagerId, Kind.LEFT, "", null, null);
	}

	private VillagerReplyPayload(RegistryFriendlyByteBuf buf) {
		this(buf.readVarInt(), Kind.values()[Math.min(buf.readVarInt(), Kind.values().length - 1)], buf.readUtf(1024), orNull(buf.readUtf(1024)),
				buf.readBoolean() ? new Phrase(buf.readUtf(1024), orNull(buf.readUtf(1024))) : null);
	}

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeVarInt(villagerId);
		buf.writeVarInt(kind.ordinal());
		buf.writeUtf(text, 1024);
		buf.writeUtf(translation == null ? "" : translation, 1024);
		buf.writeBoolean(suggestion != null);

		if (suggestion != null) {
			buf.writeUtf(suggestion.text(), 1024);
			buf.writeUtf(suggestion.translation() == null ? "" : suggestion.translation(), 1024);
		}
	}

	private static String orNull(String text) {
		return text.isEmpty() ? null : text;
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
