package io.github.dylantopic.langmod.client;

import net.fabricmc.api.ClientModInitializer;

import io.github.dylantopic.langmod.LangMod;

/**
 * Client entrypoint: microphone, speech recognition and the challenge popup.
 */
public class LangModClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		LangMod.LOGGER.info("LangMod client initialised");
	}
}
