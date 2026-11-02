package io.github.dylantopic.langmod.client;

import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

import io.github.dylantopic.langmod.LangMod;
import io.github.dylantopic.langmod.LangModConfig;
import io.github.dylantopic.langmod.client.audio.MicCapture;
import io.github.dylantopic.langmod.client.config.GameMenuButtons;
import io.github.dylantopic.langmod.client.config.ItemsScreen;
import io.github.dylantopic.langmod.client.config.LangModConfigScreen;
import io.github.dylantopic.langmod.client.config.LanguageScreen;
import io.github.dylantopic.langmod.client.hud.ChallengeHud;
import io.github.dylantopic.langmod.client.speech.SpeechService;
import io.github.dylantopic.langmod.client.voice.Pronouncer;
import io.github.dylantopic.langmod.core.lang.Language;
import io.github.dylantopic.langmod.core.tts.Voice;
import io.github.dylantopic.langmod.net.AttemptResultPayload;
import io.github.dylantopic.langmod.net.ChallengeEndPayload;
import io.github.dylantopic.langmod.net.ChallengeStartPayload;
import io.github.dylantopic.langmod.net.ChallengeStatePayload;
import io.github.dylantopic.langmod.net.ClientActionPayload;
import io.github.dylantopic.langmod.net.ClientHelloPayload;
import io.github.dylantopic.langmod.net.LearnedItemsPayload;

/**
 * Client entrypoint: microphone, speech recognition and the challenge popup.
 */
public class LangModClient implements ClientModInitializer {
	private static final KeyMapping.Category KEY_CATEGORY = KeyMapping.Category.register(LangMod.id("main"));
	private static final KeyMapping TOGGLE_DEBUG_KEY = new KeyMapping("key.langmod.toggle_debug", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F9, KEY_CATEGORY);
	private static final KeyMapping OPEN_CONFIG_KEY = new KeyMapping("key.langmod.open_config", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, KEY_CATEGORY);
	private static final KeyMapping CHOOSE_LANGUAGE_KEY = new KeyMapping("key.langmod.choose_language", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, KEY_CATEGORY);
	private static final KeyMapping HEAR_AGAIN_KEY = new KeyMapping("key.langmod.hear_again", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, KEY_CATEGORY);
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
		ClientPlayNetworking.registerGlobalReceiver(AttemptResultPayload.TYPE, (payload, context) -> challenge.onAttemptResult(payload));
		ClientPlayNetworking.registerGlobalReceiver(LearnedItemsPayload.TYPE, (payload, context) -> {
			if (Minecraft.getInstance().screen instanceof ItemsScreen items) {
				items.onLearnedItems(payload);
			}
		});

		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			sendHello();

			if (!speech.ready()) {
				ClientMessages.error(speech.statusMessage());
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			challenge.reset();
			Pronouncer.get().stop();
		});
		KeyMappingHelper.registerKeyMapping(TOGGLE_DEBUG_KEY);
		KeyMappingHelper.registerKeyMapping(OPEN_CONFIG_KEY);
		KeyMappingHelper.registerKeyMapping(CHOOSE_LANGUAGE_KEY);
		KeyMappingHelper.registerKeyMapping(HEAR_AGAIN_KEY);
		GameMenuButtons.register();
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			challenge.tick();
			ChallengeController.get().tick(client);
			MicTest.tick();

			while (TOGGLE_DEBUG_KEY.consumeClick()) {
				runAction(ClientActionPayload.Action.TOGGLE_DEBUG);
			}

			while (OPEN_CONFIG_KEY.consumeClick()) {
				runAction(ClientActionPayload.Action.OPEN_CONFIG);
			}

			while (CHOOSE_LANGUAGE_KEY.consumeClick()) {
				runAction(ClientActionPayload.Action.OPEN_LANGUAGE_MENU);
			}

			while (HEAR_AGAIN_KEY.consumeClick()) {
				ChallengeController.get().hearAgain();
			}
		});

		// Load the Whisper model in the background as soon as the game has started.
		speech.addListener(() -> Minecraft.getInstance().execute(LangModClient::onSpeechStatusChanged));
		ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
			speech.start();

			// Finds the voices for saying names, so they are known before the first mistake.
			if (LangModConfig.get().sayNameWhenWrong) {
				Pronouncer.get().start();
			}
		});
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			speech.shutdown();
			Pronouncer.get().shutdown();
		});

		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, LangMod.id("challenge"), ChallengeHud::extract);
	}

	/** Tells the server whether this client can take part in challenges. */
	public static void sendHello() {
		if (Minecraft.getInstance().getConnection() != null && ClientPlayNetworking.canSend(ClientHelloPayload.TYPE)) {
			SpeechService speech = SpeechService.get();
			ChallengeController controller = ChallengeController.get();
			boolean ready = speech.ready() && controller.micAvailable();
			String info = !speech.ready() ? speech.status() + ": " + speech.statusMessage()
					: controller.micAvailable() ? speech.description() : controller.micError();
			ClientPlayNetworking.send(new ClientHelloPayload(ClientHelloPayload.PROTOCOL_VERSION, ready, info, LangModConfig.get().language));
		}
	}

	/** Switches the language being learned and tells the server (which gives back the item of a running challenge). */
	public static void selectLanguage(Language language) {
		if (LangModConfig.get().languageEnum() == language) {
			return;
		}

		LangModConfig.update(config -> config.language = language.id());
		sendHello();
		ClientMessages.info("Now learning " + language.englishName() + " (" + language.nativeName() + "). Each language keeps its own progress.");
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
			case TOGGLE_DEBUG -> {
				boolean on = LangModConfig.update(config -> config.debugOverlay = !config.debugOverlay).debugOverlay;
				ClientMessages.info("Debug overlay " + (on ? "on" : "off") + ".");
			}
			case RELOAD -> {
				// The integrated server already re-read langmod.json. This reloads the model if speech settings
				// changed (a chat message follows when it is ready) and retries a microphone that failed.
				SpeechService.get().reload();
				ChallengeController.get().clearMicError();

				if (Pronouncer.get().status() != Pronouncer.Status.IDLE) {
					Pronouncer.get().reload(); // also sees voices added in Windows meanwhile
				}
			}
			case OPEN_CONFIG -> Minecraft.getInstance().setScreen(new LangModConfigScreen(null));
			case OPEN_LANGUAGE_MENU -> Minecraft.getInstance().setScreen(new LanguageScreen(null));
			case OPEN_ITEMS -> Minecraft.getInstance().setScreen(new ItemsScreen(null));
			case LIST_VOICES -> {
				ClientMessages.info("Looking for voices...");
				Pronouncer.get().reload().whenComplete((voices, error) -> Minecraft.getInstance().execute(LangModClient::listVoices));
			}
		}
	}

	/** /langmod voices: the voices installed and which one says each language's names. */
	private static void listVoices() {
		Pronouncer pronouncer = Pronouncer.get();

		if (pronouncer.status() == Pronouncer.Status.UNAVAILABLE) {
			ClientMessages.error("Names can't be said out loud: " + pronouncer.problem());
			return;
		}

		List<Voice> voices = pronouncer.voices();
		ClientMessages.info(voices.isEmpty() ? "No voices are installed in Windows."
				: "Voices installed in Windows: " + String.join(", ", voices.stream().map(Voice::describe).toList()) + ".");

		for (Language language : Language.values()) {
			String voice = pronouncer.voiceFor(language).map(Voice::describe).orElse("none - add a " + Pronouncer.voiceLanguage(language) + " voice");
			ClientMessages.info(" - " + language.englishName() + ": " + voice);
		}

		ClientMessages.info("Add voices in Windows: Settings > Time & language > Speech > Add voices. Choose one in the LangMod settings.");
	}
}
