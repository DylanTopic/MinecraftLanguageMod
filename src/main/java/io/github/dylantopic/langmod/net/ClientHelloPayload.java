package io.github.dylantopic.langmod.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import io.github.dylantopic.langmod.LangMod;

/**
 * Client -> server: sent on join and whenever speech recognition becomes ready or unavailable.
 * The server only starts challenges for players whose speech recognition is ready, so a missing
 * or still-loading model never makes items disappear.
 */
public record ClientHelloPayload(int protocolVersion, boolean speechReady, String speechInfo) implements CustomPacketPayload {
	public static final int PROTOCOL_VERSION = 1;
	public static final Type<ClientHelloPayload> TYPE = new Type<>(LangMod.id("client_hello"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ClientHelloPayload> CODEC =
			CustomPacketPayload.codec(ClientHelloPayload::write, ClientHelloPayload::new);

	private ClientHelloPayload(RegistryFriendlyByteBuf buf) {
		this(buf.readVarInt(), buf.readBoolean(), buf.readUtf());
	}

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeVarInt(protocolVersion);
		buf.writeBoolean(speechReady);
		buf.writeUtf(speechInfo);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
