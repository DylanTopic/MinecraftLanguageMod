package io.github.dylantopic.langmod.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

import io.github.dylantopic.langmod.LangMod;
import io.github.dylantopic.langmod.client.hud.ChallengeHud;
import io.github.dylantopic.langmod.net.ChallengeEndPayload;
import io.github.dylantopic.langmod.net.ChallengeStartPayload;
import io.github.dylantopic.langmod.net.ChallengeStatePayload;
import io.github.dylantopic.langmod.net.ClientHelloPayload;

/**
 * Client entrypoint: microphone, speech recognition and the challenge popup.
 */
public class LangModClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientChallenge challenge = ClientChallenge.get();

		// Fabric runs these handlers on the render thread.
		ClientPlayNetworking.registerGlobalReceiver(ChallengeStartPayload.TYPE, (payload, context) -> challenge.onStart(payload));
		ClientPlayNetworking.registerGlobalReceiver(ChallengeStatePayload.TYPE, (payload, context) -> challenge.onState(payload));
		ClientPlayNetworking.registerGlobalReceiver(ChallengeEndPayload.TYPE, (payload, context) -> challenge.onEnd(payload));

		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> sendHello());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> challenge.reset());
		ClientTickEvents.END_CLIENT_TICK.register(client -> challenge.tick());

		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, LangMod.id("challenge"), ChallengeHud::extract);
	}

	/** Tells the server whether this client can take part in challenges. */
	public static void sendHello() {
		if (ClientPlayNetworking.canSend(ClientHelloPayload.TYPE)) {
			// Stage 2: no speech recognition yet, so the challenge just times out.
			ClientPlayNetworking.send(new ClientHelloPayload(ClientHelloPayload.PROTOCOL_VERSION, true, "stage 2: popup only"));
		}
	}
}
