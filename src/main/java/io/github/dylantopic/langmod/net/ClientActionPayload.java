package io.github.dylantopic.langmod.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import io.github.dylantopic.langmod.LangMod;

/**
 * Server -> client: run a client-side part of a {@code /langmod} command (everything that needs
 * the microphone or the screen happens on the client).
 */
public record ClientActionPayload(Action action) implements CustomPacketPayload {
	public static final Type<ClientActionPayload> TYPE = new Type<>(LangMod.id("client_action"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ClientActionPayload> CODEC =
			CustomPacketPayload.codec(ClientActionPayload::write, ClientActionPayload::new);

	public enum Action {
		/** /langmod test: record one utterance, transcribe it and print the result. */
		MIC_TEST,
		/** /langmod mics: list the microphones Java can see. */
		LIST_MICS,
		/** /langmod debug: toggle the debug overlay. */
		TOGGLE_DEBUG,
		/** /langmod reload: reload the client side of the config (speech model, colours). */
		RELOAD,
		/** /langmod config: open the settings screen (also reachable through Mod Menu). */
		OPEN_CONFIG,
		/** /langmod language: open the menu for choosing the language to learn. */
		OPEN_LANGUAGE_MENU
	}

	private ClientActionPayload(RegistryFriendlyByteBuf buf) {
		this(Action.values()[buf.readVarInt()]);
	}

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeVarInt(action.ordinal());
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
