package io.github.dylantopic.langmod.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

import io.github.dylantopic.langmod.LangMod;

/**
 * Server -> client: a new challenge started. Shows the popup and (from stage 3) opens the mic.
 *
 * @param challengeId id echoed back by the client with every status and transcription
 * @param stack the item being held (for the icon)
 * @param translationKey the item's translation key (the client shows its English name)
 * @param hanzi the expected Chinese name
 * @param pinyin expected pinyin in tone numbers, e.g. {@code ping2 guo3}
 * @param timeLimitTicks time to answer
 * @param maxAttempts wrong answers allowed
 * @param queued new items waiting for their own challenge
 * @param longName true if the name is over the configured length and lenient matching applies
 */
public record ChallengeStartPayload(int challengeId, ItemStack stack, String translationKey, String hanzi, String pinyin,
		int timeLimitTicks, int maxAttempts, int queued, boolean longName) implements CustomPacketPayload {
	public static final Type<ChallengeStartPayload> TYPE = new Type<>(LangMod.id("challenge_start"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ChallengeStartPayload> CODEC =
			CustomPacketPayload.codec(ChallengeStartPayload::write, ChallengeStartPayload::new);

	private ChallengeStartPayload(RegistryFriendlyByteBuf buf) {
		this(buf.readVarInt(), ItemStack.OPTIONAL_STREAM_CODEC.decode(buf), buf.readUtf(), buf.readUtf(), buf.readUtf(),
				buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean());
	}

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeVarInt(challengeId);
		ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, stack);
		buf.writeUtf(translationKey);
		buf.writeUtf(hanzi);
		buf.writeUtf(pinyin);
		buf.writeVarInt(timeLimitTicks);
		buf.writeVarInt(maxAttempts);
		buf.writeVarInt(queued);
		buf.writeBoolean(longName);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
