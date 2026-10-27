package io.github.dylantopic.langmod.client;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

import io.github.dylantopic.langmod.LangMod;
import io.github.dylantopic.langmod.client.audio.MicCapture;
import io.github.dylantopic.langmod.client.hud.ChallengeHud;
import io.github.dylantopic.langmod.client.speech.SpeechService;
import io.github.dylantopic.langmod.net.ChallengeEndPayload;
import io.github.dylantopic.langmod.net.ChallengeStartPayload;
import io.github.dylantopic.langmod.net.ChallengeStatePayload;
import io.github.dylantopic.langmod.net.ClientActionPayload;
import io.github.dylantopic.langmod.net.ClientHelloPayload;

/**
 * Client entrypoint: microphone, speech recognition and the challenge popup.
 */
public class LangModClient implements ClientModInitializer {
	private static SpeechService.Status lastNotifiedStatus;

	@Override
	public void onInitializeClient() {
		ClientChallenge challenge = ClientChallenge.get();
		SpeechService speech = SpeechService.get();

		// Fabric runs these handlers on the render thread.
		ClientPlayNetworking.registerGlobalReceiver(ChallengeStartPayload.TYPE, (payload, context) -> challenge.onStart(payload));
		ClientPlayNetworking.registerGlobalReceiver(ChallengeStatePayload.TYPE, (payload, context) -> challenge.onState(payload));
		ClientPlayNetworking.registerGlobalReceiver(ChallengeEndPayload.TYPE, (payload, context) -> challenge.onEnd(payload));
		ClientPlayNetworking.registerGlobalReceiver(ClientActionPayload.TYPE, (payload, context) -> runAction(payload.action()));

		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			sendHello();

			if (!speech.ready()) {
				ClientMessages.error(speech.statusMessage());
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> challenge.reset());
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			challenge.tick();
			MicTest.tick();
		});

		// Load the Whisper model in the background as soon as the game has started.
		speech.addListener(() -> Minecraft.getInstance().execute(LangModClient::onSpeechStatusChanged));
		ClientLifecycleEvents.CLIENT_STARTED.register(client -> speech.start());
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> speech.shutdown());

		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, LangMod.id("challenge"), ChallengeHud::extract);
	}

	/** Tells the server whether this client can take part in challenges. */
	public static void sendHello() {
		if (Minecraft.getInstance().getConnection() != null && ClientPlayNetworking.canSend(ClientHelloPayload.TYPE)) {
			SpeechService speech = SpeechService.get();
			String info = speech.ready() ? speech.description() : speech.status() + ": " + speech.statusMessage();
			ClientPlayNetworking.send(new ClientHelloPayload(ClientHelloPayload.PROTOCOL_VERSION, speech.ready(), info));
		}
	}

	private static void onSpeechStatusChanged() {
		sendHello();
		SpeechService speech = SpeechService.get();
		SpeechService.Status status = speech.status();

		if (status == lastNotifiedStatus) {
			return;
		}

		lastNotifiedStatus = status;

		if (status == SpeechService.Status.MODEL_MISSING || status == SpeechService.Status.FAILED) {
			Minecraft minecraft = Minecraft.getInstance();
			String title = status == SpeechService.Status.MODEL_MISSING ? "LangMod: Whisper model missing" : "LangMod: speech recognition failed";
			minecraft.getToastManager().addToast(SystemToast.multiline(minecraft, SystemToast.SystemToastId.NARRATOR_TOGGLE,
					Component.literal(title), Component.literal(speech.statusMessage())));
			ClientMessages.error(speech.statusMessage());
		} else if (status == SpeechService.Status.READY) {
			ClientMessages.info("Speech recognition ready: " + speech.description());
		}
	}

	private static void runAction(ClientActionPayload.Action action) {
		switch (action) {
			case MIC_TEST -> MicTest.start();
			case LIST_MICS -> {
				List<String> names = MicCapture.deviceNames();
				ClientMessages.info(names.isEmpty() ? "No microphones found." : "Microphones (set micDevice in config/langmod/langmod.json to part of a name):");

				for (String name : names) {
					ClientMessages.info(" - " + name);
				}
			}
			case TOGGLE_DEBUG, RELOAD -> ClientMessages.info("Not available yet.");
		}
	}
}
