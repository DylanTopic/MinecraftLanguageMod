package io.github.dylantopic.langmod;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.resources.Identifier;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import io.github.dylantopic.langmod.challenge.ChallengeManager;
import io.github.dylantopic.langmod.data.PlayerLangData;
import io.github.dylantopic.langmod.net.LangModNetworking;

/**
 * Common (server-side) entrypoint. All game logic lives on the logical server so that the
 * mod keeps working when multiplayer support is added; the client only handles audio and UI.
 */
public class LangMod implements ModInitializer {
	public static final String MOD_ID = "langmod";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LangModConfig.load();
		PlayerLangData.init();
		LangModNetworking.register();

		ChallengeManager challenges = ChallengeManager.get();
		challenges.setDictionary(ModResources.loadItemDictionary());

		ServerPlayerEvents.JOIN.register(challenges::onJoin);
		ServerPlayerEvents.LEAVE.register(challenges::onLeave);
		ServerTickEvents.END_SERVER_TICK.register(challenges::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(challenges::onServerStopping);
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
