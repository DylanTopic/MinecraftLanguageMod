package io.github.dylantopic.langmod.net;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import io.github.dylantopic.langmod.challenge.ChallengeManager;

/** Registers the mod's payloads (both directions) and the server-side receivers. */
public final class LangModNetworking {
	private LangModNetworking() {
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(ChallengeStartPayload.TYPE, ChallengeStartPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ChallengeStatePayload.TYPE, ChallengeStatePayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ChallengeEndPayload.TYPE, ChallengeEndPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ClientHelloPayload.TYPE, ClientHelloPayload.CODEC);

		// Fabric runs these handlers on the server thread.
		ServerPlayNetworking.registerGlobalReceiver(ClientHelloPayload.TYPE,
				(payload, context) -> ChallengeManager.get().onClientHello(context.player(), payload));
	}

	/** Sends a payload if the player's client has the mod (it is silently dropped otherwise). */
	public static void send(ServerPlayer player, CustomPacketPayload payload) {
		if (ServerPlayNetworking.canSend(player, payload.type())) {
			ServerPlayNetworking.send(player, payload);
		}
	}
}
