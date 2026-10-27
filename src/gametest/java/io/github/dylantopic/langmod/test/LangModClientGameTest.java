package io.github.dylantopic.langmod.test;

import java.util.List;
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
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;

import io.github.dylantopic.langmod.LangMod;
import io.github.dylantopic.langmod.LangModConfig;
import io.github.dylantopic.langmod.challenge.ChallengeManager;
import io.github.dylantopic.langmod.client.ChallengeController;
import io.github.dylantopic.langmod.client.ClientChallenge;
import io.github.dylantopic.langmod.client.MicTest;
import io.github.dylantopic.langmod.client.speech.SpeechService;
import io.github.dylantopic.langmod.core.Matcher;
import io.github.dylantopic.langmod.data.PlayerLangData;
import io.github.dylantopic.langmod.net.AttemptResultPayload;
import io.github.dylantopic.langmod.net.ChallengeEndPayload;

/**
 * End-to-end test run with {@code ./gradlew runClientGameTest} (and in CI under xvfb). A scripted
 * engine stands in for Whisper and clips are submitted directly instead of using a microphone.
 * Screenshots are written to build/run/clientGameTest/screenshots.
 */
@SuppressWarnings("UnstableApiUsage")
public class LangModClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		context.waitForScreen(TitleScreen.class);
		context.takeScreenshot("langmod-01-title-screen");

		LangModConfig config = LangModConfig.get().copy();
		config.timerSeconds = 6;
		config.maxAttempts = 3;
		config.matchMode = "strict";
		config.debugOverlay = true;
		LangModConfig.setForTesting(config);

		ScriptedSpeechEngine speech = new ScriptedSpeechEngine();
		SpeechService.get().useEngineForTesting(speech);
		ChallengeController.get().setUseMicrophoneForTesting(false);
		waitUntil(context, "the test speech engine", () -> SpeechService.get().ready());

		TestWorldSave save;
		UUID uuid;

		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			save = singleplayer.getWorldSave();
			singleplayer.getClientLevel().waitForChunksRender();
			context.takeScreenshot("langmod-02-in-world");
			uuid = context.computeOnClient(minecraft -> minecraft.player.getUUID());
			waitUntilReady(context, singleplayer, uuid);

			// /langmod test path (minus the microphone): Traditional output is shown simplified with pinyin.
			speech.enqueue("蘋果。");
			context.runOnClient(minecraft -> MicTest.runOnClipForTesting(new float[16_000]));
			context.waitFor(minecraft -> MicTest.current() == null, 200);
			check("苹果".equals(MicTest.lastHeard()), "mic test shows 苹果 for Whisper output 蘋果。 (got " + MicTest.lastHeard() + ")");
			context.takeScreenshot("langmod-03-mic-test-chat");

			// 1. A new item: the pickup turns into a challenge; saying it right hands it over.
			drop(singleplayer, uuid, new ItemStack(Items.APPLE, 3));
			waitForChallenge(context, "苹果");
			check(count(singleplayer, uuid, Items.APPLE) == 0, "the apples are held back during the challenge");
			context.takeScreenshot("langmod-04-challenge-popup");
			answer(context, speech, "蘋果。");
			context.waitFor(minecraft -> ClientChallenge.get().active() == null, 100);
			context.takeScreenshot("langmod-05-correct");
			check(ClientChallenge.get().endOutcome() == ChallengeEndPayload.Outcome.SUCCESS, "correct answer succeeds");
			waitUntil(context, "apples in the inventory", () -> count(singleplayer, uuid, Items.APPLE) == 3);
			check(data(singleplayer, uuid).isLearned("minecraft:apple"), "apple is learned");

			// 2. Once learned, pickups are normal.
			drop(singleplayer, uuid, new ItemStack(Items.APPLE));
			waitUntil(context, "a normal apple pickup", () -> count(singleplayer, uuid, Items.APPLE) == 4);
			check(ClientChallenge.get().active() == null, "no challenge for a learned item");

			// 3. Wrong answers: wrong tone, then nothing heard (not counted), then two wrong words -> gone.
			drop(singleplayer, uuid, new ItemStack(Items.DIRT, 5));
			waitForChallenge(context, "泥土");
			AttemptResultPayload result = answer(context, speech, "你吐"); // nǐ tǔ instead of ní tǔ
			check(result.verdict() == AttemptResultPayload.Verdict.WRONG, "wrong tone is wrong in strict mode");
			check(result.expectedStatus().equals(List.of(Matcher.Status.TONE, Matcher.Status.MATCH)), "first syllable marked as wrong tone: " + result.expectedStatus());
			context.waitTicks(5);
			context.takeScreenshot("langmod-06-wrong-tone");
			result = answer(context, speech, "");
			check(result.verdict() == AttemptResultPayload.Verdict.NO_SPEECH && result.attemptsUsed() == 1, "silence is not counted");
			result = answer(context, speech, "香蕉");
			check(result.verdict() == AttemptResultPayload.Verdict.WRONG && result.attemptsUsed() == 2, "second wrong attempt");
			answer(context, speech, "西瓜");
			context.waitFor(minecraft -> ClientChallenge.get().active() == null, 100);
			context.takeScreenshot("langmod-07-vanished");
			check(ClientChallenge.get().endOutcome() == ChallengeEndPayload.Outcome.FAILED, "three wrong answers fail");
			check(count(singleplayer, uuid, Items.DIRT) == 0, "the dirt vanished");
			check(!data(singleplayer, uuid).isLearned("minecraft:dirt"), "dirt stays unlearned");
			check(data(singleplayer, uuid).failures().getOrDefault("minecraft:dirt", 0) == 1, "the failure is recorded");

			// 4. Not answering in time also makes the item vanish.
			drop(singleplayer, uuid, new ItemStack(Items.STICK, 2));
			waitForChallenge(context, "木棍");
			context.waitFor(minecraft -> ClientChallenge.get().active() == null, 20 * 12);
			check(ClientChallenge.get().endOutcome() == ChallengeEndPayload.Outcome.FAILED, "time running out fails");
			check(count(singleplayer, uuid, Items.STICK) == 0, "the sticks vanished");

			// 5. Quitting mid-challenge gives the item back.
			drop(singleplayer, uuid, new ItemStack(Items.TORCH, 7));
			waitForChallenge(context, "火把");
			check(count(singleplayer, uuid, Items.TORCH) == 0, "the torches are held back");
		}

		try (TestSingleplayerContext singleplayer = save.open()) {
			singleplayer.getClientLevel().waitForChunksRender();
			waitUntilReady(context, singleplayer, uuid);
			check(count(singleplayer, uuid, Items.TORCH) == 7, "torches were given back when the world was closed");
			check(count(singleplayer, uuid, Items.APPLE) == 4, "apples are still there");
			PlayerLangData data = data(singleplayer, uuid);
			check(data.isLearned("minecraft:apple") && !data.isLearned("minecraft:torch"), "learned items were saved");
			check(data.failures().getOrDefault("minecraft:dirt", 0) == 1 && data.pending().isEmpty(), "failures saved, nothing pending");
			context.takeScreenshot("langmod-08-reloaded");
		}

		LangMod.LOGGER.info("LANGMOD-GAMETEST: all checks passed");
	}

	/** Submits a clip whose transcription will be {@code text} and waits for the server's verdict. */
	private static AttemptResultPayload answer(ClientGameTestContext context, ScriptedSpeechEngine speech, String text) {
		AttemptResultPayload before = context.computeOnClient(minecraft -> ClientChallenge.get().lastResult());
		speech.enqueue(text);
		context.runOnClient(minecraft -> ChallengeController.get().submitClipForTesting(new float[8_000]));
		context.waitFor(minecraft -> ClientChallenge.get().lastResult() != before || ClientChallenge.get().active() == null, 200);
		AttemptResultPayload result = context.computeOnClient(minecraft -> ClientChallenge.get().lastResult());
		LangMod.LOGGER.info("LANGMOD-GAMETEST: answered '{}' -> {}", text, result == null ? "(challenge over)" : result.verdict() + " " + result.expectedStatus());
		return result;
	}

	private static void waitForChallenge(ClientGameTestContext context, String hanzi) {
		context.waitFor(minecraft -> ClientChallenge.get().active() != null, 20 * 8);
		String shown = context.computeOnClient(minecraft -> ClientChallenge.get().active().entry().hanzi());
		check(hanzi.equals(shown), "challenge for " + hanzi + " (got " + shown + ")");
	}

	private static void waitUntilReady(ClientGameTestContext context, TestSingleplayerContext singleplayer, UUID uuid) {
		waitUntil(context, "the client's hello to reach the server",
				() -> singleplayer.getServer().computeOnServer(server -> ChallengeManager.get().isSpeechReady(uuid)));
	}

	private static void drop(TestSingleplayerContext singleplayer, UUID uuid, ItemStack stack) {
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = server.getPlayerList().getPlayer(uuid);
			ItemEntity item = new ItemEntity(player.level(), player.getX(), player.getY() + 0.5, player.getZ(), stack);
			item.setPickUpDelay(0);
			player.level().addFreshEntity(item);
		});
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

	private static PlayerLangData data(TestSingleplayerContext singleplayer, UUID uuid) {
		return singleplayer.getServer().computeOnServer(server -> data(server, uuid));
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
