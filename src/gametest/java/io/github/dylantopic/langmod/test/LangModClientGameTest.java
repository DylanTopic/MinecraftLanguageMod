package io.github.dylantopic.langmod.test;

import java.util.UUID;
import java.util.function.BooleanSupplier;

import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import io.github.dylantopic.langmod.LangMod;
import io.github.dylantopic.langmod.LangModConfig;
import io.github.dylantopic.langmod.challenge.ChallengeManager;
import io.github.dylantopic.langmod.client.ClientChallenge;
import io.github.dylantopic.langmod.client.MicTest;
import io.github.dylantopic.langmod.client.speech.SpeechService;
import io.github.dylantopic.langmod.data.PlayerLangData;

/**
 * End-to-end test run with {@code ./gradlew runClientGameTest} (and in CI under xvfb).
 * Screenshots are written to build/run/clientGameTest/screenshots.
 */
@SuppressWarnings("UnstableApiUsage")
public class LangModClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		context.waitForScreen(TitleScreen.class);
		context.takeScreenshot("langmod-01-title-screen");

		LangModConfig config = LangModConfig.get().copy();
		config.timerSeconds = 4;
		LangModConfig.setForTesting(config);

		// No microphone or Whisper model in CI: a scripted engine stands in for Whisper.
		ScriptedSpeechEngine speech = new ScriptedSpeechEngine();
		SpeechService.get().useEngineForTesting(speech);
		waitUntil(context, "the test speech engine", () -> SpeechService.get().ready());

		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getClientLevel().waitForChunksRender();
			context.takeScreenshot("langmod-02-in-world");
			UUID uuid = context.computeOnClient(minecraft -> minecraft.player.getUUID());

			waitUntil(context, "the client's hello to reach the server",
					() -> singleplayer.getServer().computeOnServer(server -> ChallengeManager.get().isSpeechReady(uuid)));

			// /langmod test path (minus the microphone): Traditional output is shown simplified with pinyin.
			speech.enqueue("蘋果。");
			context.runOnClient(minecraft -> MicTest.runOnClipForTesting(new float[16_000]));
			context.waitFor(minecraft -> MicTest.current() == null, 200);
			check("苹果".equals(MicTest.lastHeard()), "mic test shows 苹果 for Whisper output 蘋果。 (got " + MicTest.lastHeard() + ")");
			context.takeScreenshot("langmod-03-mic-test-chat");

			// An apple the player has never picked up: the pickup must turn into a challenge.
			singleplayer.getServer().runOnServer(server -> dropOnPlayer(server, uuid, new ItemStack(Items.APPLE, 3)));
			context.waitFor(minecraft -> ClientChallenge.get().active() != null, 200);
			context.waitTicks(5);
			context.takeScreenshot("langmod-04-challenge-popup");

			check(ClientChallenge.get().active().entry().hanzi().equals("苹果"), "popup shows 苹果");
			check(count(singleplayer, uuid, Items.APPLE) == 0, "the apples are held back during the challenge");

			// Stage 2 has no speech yet: when time runs out the apples are handed over, not learned.
			context.waitFor(minecraft -> ClientChallenge.get().active() == null, 20 * 10);
			context.waitTicks(2);
			check(count(singleplayer, uuid, Items.APPLE) == 3, "the apples are handed over after the challenge");
			check(!singleplayer.getServer().computeOnServer(server -> data(server, uuid).isLearned("minecraft:apple")),
					"apple is not marked as learned");
			check(singleplayer.getServer().computeOnServer(server -> data(server, uuid).pending().isEmpty()),
					"no items are left pending");
			LangMod.LOGGER.info("LANGMOD-GAMETEST: mic test path, pickup interception and popup OK");
		}
	}

	private static void dropOnPlayer(MinecraftServer server, UUID uuid, ItemStack stack) {
		ServerPlayer player = server.getPlayerList().getPlayer(uuid);
		ItemEntity item = new ItemEntity(player.level(), player.getX(), player.getY() + 0.5, player.getZ(), stack);
		item.setPickUpDelay(0);
		player.level().addFreshEntity(item);
	}

	private static int count(TestSingleplayerContext singleplayer, UUID uuid, Item item) {
		return singleplayer.getServer().computeOnServer(server -> {
			ServerPlayer player = server.getPlayerList().getPlayer(uuid);
			int total = 0;

			for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
				ItemStack stack = player.getInventory().getItem(slot);

				if (stack.is(item)) {
					total += stack.getCount();
				}
			}

			return total;
		});
	}

	private static PlayerLangData data(MinecraftServer server, UUID uuid) {
		return server.getPlayerList().getPlayer(uuid).getAttachedOrCreate(PlayerLangData.TYPE);
	}

	private static void waitUntil(ClientGameTestContext context, String what, BooleanSupplier condition) {
		for (int i = 0; i < 200; i++) {
			if (condition.getAsBoolean()) {
				return;
			}

			context.waitTick();
		}

		throw new AssertionError("Timed out waiting for " + what);
	}

	private static void check(boolean condition, String what) {
		if (!condition) {
			throw new AssertionError("Expected: " + what);
		}
	}
}
