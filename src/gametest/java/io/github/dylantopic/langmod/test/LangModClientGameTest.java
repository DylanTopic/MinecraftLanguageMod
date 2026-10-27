package io.github.dylantopic.langmod.test;

import net.minecraft.client.gui.screens.TitleScreen;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * End-to-end smoke test run with {@code ./gradlew runClientGameTest} (and in CI under xvfb).
 * Screenshots are written to build/run/clientGameTest/screenshots.
 */
@SuppressWarnings("UnstableApiUsage")
public class LangModClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		context.waitForScreen(TitleScreen.class);
		context.takeScreenshot("langmod-title-screen");

		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getClientLevel().waitForChunksRender();
			context.takeScreenshot("langmod-in-world");
		}
	}
}
