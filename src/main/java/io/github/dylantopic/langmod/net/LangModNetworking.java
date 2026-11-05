package io.github.dylantopic.langmod.net;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import io.github.dylantopic.langmod.challenge.ChallengeManager;
import io.github.dylantopic.langmod.villager.VillagerConversations;

/** Registers the mod's payloads (both directions) and the server-side receivers. */
public final class LangModNetworking {
	private LangModNetworking() {
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(ChallengeStartPayload.TYPE, ChallengeStartPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ChallengeStatePayload.TYPE, ChallengeStatePayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ChallengeEndPayload.TYPE, ChallengeEndPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ClientActionPayload.TYPE, ClientActionPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(AttemptResultPayload.TYPE, AttemptResultPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(LearnedItemsPayload.TYPE, LearnedItemsPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(VillagerChoicePayload.TYPE, VillagerChoicePayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(VillagerReplyPayload.TYPE, VillagerReplyPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ClientHelloPayload.TYPE, ClientHelloPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ClientPhasePayload.TYPE, ClientPhasePayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(TranscriptionPayload.TYPE, TranscriptionPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(LearnedItemsRequestPayload.TYPE, LearnedItemsRequestPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(VillagerActionPayload.TYPE, VillagerActionPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(VillagerSayPayload.TYPE, VillagerSayPayload.CODEC);

		// Fabric runs these handlers on the server thread.
		ServerPlayNetworking.registerGlobalReceiver(ClientHelloPayload.TYPE,
				(payload, context) -> ChallengeManager.get().onClientHello(context.player(), payload));
		ServerPlayNetworking.registerGlobalReceiver(ClientPhasePayload.TYPE,
				(payload, context) -> ChallengeManager.get().onClientPhase(context.player(), payload));
		ServerPlayNetworking.registerGlobalReceiver(TranscriptionPayload.TYPE,
				(payload, context) -> ChallengeManager.get().onTranscription(context.player(), payload));
		ServerPlayNetworking.registerGlobalReceiver(LearnedItemsRequestPayload.TYPE,
				(payload, context) -> ChallengeManager.get().onLearnedItemsRequest(context.player(), payload));
		ServerPlayNetworking.registerGlobalReceiver(VillagerActionPayload.TYPE,
				(payload, context) -> VillagerConversations.get().onAction(context.player(), payload));
		ServerPlayNetworking.registerGlobalReceiver(VillagerSayPayload.TYPE,
				(payload, context) -> VillagerConversations.get().onSay(context.player(), payload));
	}

	/** Sends a payload if the player's client has the mod (it is silently dropped otherwise). */
	public static void send(ServerPlayer player, CustomPacketPayload payload) {
		if (ServerPlayNetworking.canSend(player, payload.type())) {
			ServerPlayNetworking.send(player, payload);
		}
	}
}
