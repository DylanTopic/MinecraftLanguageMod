package io.github.dylantopic.langmod;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.resources.Identifier;

import net.fabricmc.api.ModInitializer;

/**
 * Common (server-side) entrypoint. All game logic lives on the logical server so that the
 * mod keeps working when multiplayer support is added; the client only handles audio and UI.
 */
public class LangMod implements ModInitializer {
	public static final String MOD_ID = "langmod";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("LangMod initialised");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
