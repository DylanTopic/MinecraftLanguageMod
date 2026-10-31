package io.github.dylantopic.langmod.test;

import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import org.lwjgl.glfw.GLFW;

import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.fabricmc.loader.api.FabricLoader;

import io.github.dylantopic.langmod.LangMod;
import io.github.dylantopic.langmod.LangModConfig;
import io.github.dylantopic.langmod.challenge.ChallengeManager;
import io.github.dylantopic.langmod.command.LangModCommands;
import io.github.dylantopic.langmod.client.ChallengeController;
import io.github.dylantopic.langmod.client.ClientChallenge;
import io.github.dylantopic.langmod.client.LangModClient;
import io.github.dylantopic.langmod.client.MicTest;
import io.github.dylantopic.langmod.client.config.GameMenuButtons;
import io.github.dylantopic.langmod.client.config.ItemsScreen;
import io.github.dylantopic.langmod.client.config.LangModConfigScreen;
import io.github.dylantopic.langmod.client.config.LanguageScreen;
import io.github.dylantopic.langmod.client.speech.SpeechService;
import io.github.dylantopic.langmod.core.Matcher;
import io.github.dylantopic.langmod.core.lang.Language;
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
			check(data(singleplayer, uuid).isLearned(Language.MANDARIN, "minecraft:apple"), "apple is learned");

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
			check(!data(singleplayer, uuid).isLearned(Language.MANDARIN, "minecraft:dirt"), "dirt stays unlearned");
			check(mandarin(singleplayer, uuid).failures().getOrDefault("minecraft:dirt", 0) == 1, "the failure is recorded");

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
			check(data.isLearned(Language.MANDARIN, "minecraft:apple") && !data.isLearned(Language.MANDARIN, "minecraft:torch"), "learned items were saved");
			check(data.progress(Language.MANDARIN).failures().getOrDefault("minecraft:dirt", 0) == 1 && data.pending().isEmpty(),
					"failures saved, nothing pending");
			context.takeScreenshot("langmod-08-reloaded");

			// 6. Turning the mod off mid-challenge gives the item back.
			drop(singleplayer, uuid, new ItemStack(Items.TORCH, 2));
			waitForChallenge(context, "火把");
			LangModConfig.update(c -> c.enabled = false);
			context.waitFor(minecraft -> ClientChallenge.get().active() == null, 100);
			check(ClientChallenge.get().endOutcome() == ChallengeEndPayload.Outcome.CANCELLED, "turning the mod off cancels the challenge");
			waitUntil(context, "the torches to come back", () -> count(singleplayer, uuid, Items.TORCH) == 9);
			drop(singleplayer, uuid, new ItemStack(Items.STICK));
			waitUntil(context, "a vanilla pickup while the mod is off", () -> count(singleplayer, uuid, Items.STICK) == 1);
			LangModConfig.update(c -> c.enabled = true);

			// 7. Commands.
			String apple = singleplayer.getServer().computeOnServer(server -> LangModCommands.describe(Language.MANDARIN, "minecraft:apple").getString());
			check(apple.equals("苹果 píng guǒ (Apple)"), "/langmod list line for apple (got " + apple + ")");
			check(command(singleplayer, uuid, "langmod list") == 1, "/langmod list shows one learned item");
			check(command(singleplayer, uuid, "langmod review") == 2, "/langmod review lists dirt and stick (cancelled torches don't count)");
			check(command(singleplayer, uuid, "langmod reset") == 1, "/langmod reset forgets one item");
			PlayerLangData.Progress mandarin = mandarin(singleplayer, uuid);
			check(mandarin.learned().isEmpty() && mandarin.failures().getOrDefault("minecraft:dirt", 0) == 1, "reset keeps the mistake history");
			command(singleplayer, uuid, "langmod reset everything");
			check(mandarin(singleplayer, uuid).failures().isEmpty(), "reset everything clears the mistake history");

			// 8. Settings screen, opened with /langmod config (Mod Menu opens the same screen).
			check(FabricLoader.getInstance().getEntrypointContainers("modmenu", Object.class).stream()
					.anyMatch(entrypoint -> entrypoint.getProvider().getMetadata().getId().equals(LangMod.MOD_ID)), "Mod Menu entrypoint registered");
			command(singleplayer, uuid, "langmod config");
			context.waitFor(minecraft -> minecraft.screen instanceof LangModConfigScreen, 100);
			context.takeScreenshot("langmod-09-config-screen");
			boolean english = LangModConfig.get().showEnglish;
			context.runOnClient(minecraft -> toggle(((LangModConfigScreen) minecraft.screen).option("showEnglish")));
			check(LangModConfig.get().showEnglish != english, "the settings screen changes the config");
			context.setScreen(() -> null);
			LangModConfig.update(c -> c.showEnglish = english);

			// 9. Other languages, each with its own progress. Spanish accepts an article in front.
			selectLanguage(context, singleplayer, uuid, Language.SPANISH);
			drop(singleplayer, uuid, new ItemStack(Items.APPLE, 2));
			waitForChallenge(context, "Manzana");
			context.takeScreenshot("langmod-10-spanish-popup");
			answer(context, speech, "¡La manzana!");
			context.waitFor(minecraft -> ClientChallenge.get().active() == null, 100);
			check(ClientChallenge.get().endOutcome() == ChallengeEndPayload.Outcome.SUCCESS, "'la manzana' is accepted for Manzana");
			check(data(singleplayer, uuid).isLearned(Language.SPANISH, "minecraft:apple"), "apple learned in Spanish");
			check(!data(singleplayer, uuid).isLearned(Language.MANDARIN, "minecraft:apple"), "Mandarin progress is separate");
			check(command(singleplayer, uuid, "langmod list") == 1, "/langmod list shows the Spanish progress");

			// Long Spanish names (more than 3 words) are skipped: "Letrero colgante de acacia" is picked up normally.
			drop(singleplayer, uuid, new ItemStack(Items.ACACIA_HANGING_SIGN));
			waitUntil(context, "a normal pickup of a long name", () -> count(singleplayer, uuid, Items.ACACIA_HANGING_SIGN) == 1);
			check(ClientChallenge.get().active() == null, "no challenge for a long Spanish name");

			// Korean, with the romanization shown under the name.
			selectLanguage(context, singleplayer, uuid, Language.KOREAN);
			drop(singleplayer, uuid, new ItemStack(Items.BREAD));
			waitForChallenge(context, "빵");
			check("ppang".equals(context.computeOnClient(minecraft -> ClientChallenge.get().active().entry().pronunciation())), "romanization ppang");
			context.takeScreenshot("langmod-11-korean-popup");
			AttemptResultPayload korean = answer(context, speech, "빵이요");
			check(korean.verdict() == AttemptResultPayload.Verdict.WRONG && korean.expectedStatus().equals(List.of(Matcher.Status.MATCH)),
					"extra syllables are wrong, 빵 itself matched: " + korean.expectedStatus());
			answer(context, speech, "빵.");
			context.waitFor(minecraft -> ClientChallenge.get().active() == null, 100);
			check(ClientChallenge.get().endOutcome() == ChallengeEndPayload.Outcome.SUCCESS, "빵 is accepted");
			check(data(singleplayer, uuid).isLearned(Language.KOREAN, "minecraft:bread"), "bread learned in Korean");

			// Switching language during a challenge gives the item back.
			drop(singleplayer, uuid, new ItemStack(Items.DIRT, 5));
			waitForChallenge(context, "흙");
			selectLanguage(context, singleplayer, uuid, Language.FRENCH);
			context.waitFor(minecraft -> ClientChallenge.get().active() == null, 100);
			check(ClientChallenge.get().endOutcome() == ChallengeEndPayload.Outcome.CANCELLED, "switching language cancels the challenge");
			waitUntil(context, "the dirt to come back", () -> count(singleplayer, uuid, Items.DIRT) == 5);

			// Japanese: the reading and romaji are shown under a name in kanji, and an answer in kana counts.
			selectLanguage(context, singleplayer, uuid, Language.JAPANESE);
			drop(singleplayer, uuid, new ItemStack(Items.TORCH, 2));
			waitForChallenge(context, "松明");
			check("たいまつ  taimatsu".equals(context.computeOnClient(minecraft -> ClientChallenge.get().active().entry().pronunciation())),
					"reading and romaji under 松明");
			context.takeScreenshot("langmod-16-japanese-popup");
			answer(context, speech, "たいまつ。");
			context.waitFor(minecraft -> ClientChallenge.get().active() == null, 100);
			check(ClientChallenge.get().endOutcome() == ChallengeEndPayload.Outcome.SUCCESS, "たいまつ is accepted for 松明");
			check(data(singleplayer, uuid).isLearned(Language.JAPANESE, "minecraft:torch"), "torch learned in Japanese");
			selectLanguage(context, singleplayer, uuid, Language.FRENCH);

			// The language menu: one button per language plus Done, all on screen.
			command(singleplayer, uuid, "langmod language");
			context.waitFor(minecraft -> minecraft.screen instanceof LanguageScreen, 100);
			context.takeScreenshot("langmod-12-language-menu");
			check(context.computeOnClient(minecraft -> {
				List<Button> buttons = minecraft.screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast).toList();
				return buttons.size() == Language.values().length + 1 && fitWithoutOverlap(List.<AbstractWidget>copyOf(buttons), minecraft.screen);
			}), "the language menu shows every language, on screen and clear of each other and Done");
			context.setScreen(() -> null);

			// The "Learning" option of the settings screen switches language and reopens the screen for it.
			command(singleplayer, uuid, "langmod config");
			context.waitFor(minecraft -> minecraft.screen instanceof LangModConfigScreen, 100);
			check(context.computeOnClient(minecraft -> ((LangModConfigScreen) minecraft.screen).option("toneColor1") == null),
					"no tone colors while learning French");
			Screen settings = context.computeOnClient(minecraft -> minecraft.screen);
			context.runOnClient(minecraft -> select(((LangModConfigScreen) minecraft.screen).option("language"), Language.MANDARIN));
			context.waitFor(minecraft -> minecraft.screen instanceof LangModConfigScreen screen && screen != settings, 100);
			waitUntil(context, "the server to switch to Mandarin",
					() -> singleplayer.getServer().computeOnServer(server -> ChallengeManager.get().language(uuid) == Language.MANDARIN));
			check(LangModConfig.get().languageEnum() == Language.MANDARIN, "the settings screen changes the language");
			check(context.computeOnClient(minecraft -> ((LangModConfigScreen) minecraft.screen).option("toneColor1") != null),
					"tone colors while learning Mandarin");
			context.setScreen(() -> null);

			// 10. The game menu (Esc) gets Items and Practice Language buttons beside the vanilla ones.
			context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
			context.waitFor(minecraft -> minecraft.screen instanceof PauseScreen, 100);
			context.takeScreenshot("langmod-13-game-menu");
			check(context.computeOnClient(minecraft -> menuButtonsFit(minecraft.screen)), "Items and Practice Language are on screen, clear of the other buttons");
			context.clickScreenButton(GameMenuButtons.LANGUAGE);
			context.waitFor(minecraft -> minecraft.screen instanceof LanguageScreen, 100);
			context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
			context.waitFor(minecraft -> minecraft.screen instanceof PauseScreen, 100);

			// The Items screen lists every item and can be searched; an item turned off there is picked up normally.
			context.clickScreenButton(GameMenuButtons.ITEMS);
			context.waitFor(minecraft -> minecraft.screen instanceof ItemsScreen, 100);
			// The game is paused here; the server still has to answer which items are learned.
			context.waitFor(minecraft -> minecraft.screen instanceof ItemsScreen items && items.learnedLoaded(), 100);
			int allItems = context.computeOnClient(minecraft -> ((ItemsScreen) minecraft.screen).shownItemIds().size());
			check(allItems > 1000, "the Items screen lists every item: " + allItems);
			context.getInput().typeChars("carrot");
			context.waitFor(minecraft -> minecraft.screen instanceof ItemsScreen items && items.shownItemIds().size() < allItems, 100);
			List<String> found = context.computeOnClient(minecraft -> ((ItemsScreen) minecraft.screen).shownItemIds());
			check(found.contains("minecraft:carrot") && found.size() < 10, "searching for carrot: " + found);
			context.takeScreenshot("langmod-14-items");
			context.runOnClient(minecraft -> ((ItemsScreen) minecraft.screen).toggle("minecraft:carrot"));
			check(!LangModConfig.get().isItemEnabled(Language.MANDARIN, "minecraft:carrot"), "the Items screen turns carrots off");
			context.setScreen(() -> null);
			drop(singleplayer, uuid, new ItemStack(Items.CARROT, 3));
			waitUntil(context, "a normal pickup of an item turned off", () -> count(singleplayer, uuid, Items.CARROT) == 3);
			check(ClientChallenge.get().active() == null, "no challenge for an item turned off");
			LangModConfig.update(c -> c.setItemEnabled(Language.MANDARIN, "minecraft:carrot", true));

			// In Spanish, where the apple was learned, the list marks it. /langmod items opens the same screen.
			selectLanguage(context, singleplayer, uuid, Language.SPANISH);
			command(singleplayer, uuid, "langmod items");
			context.waitFor(minecraft -> minecraft.screen instanceof ItemsScreen items && items.learnedLoaded(), 100);
			int learnedSpanish = context.computeOnClient(minecraft -> ((ItemsScreen) minecraft.screen).learnedCount());
			check(learnedSpanish == 1, "the Items screen marks the one item learned in Spanish: " + learnedSpanish);
			context.takeScreenshot("langmod-15-items-spanish");
			context.setScreen(() -> null);
			selectLanguage(context, singleplayer, uuid, Language.MANDARIN);

			check(command(singleplayer, uuid, "langmod reload") == 1, "/langmod reload");
			waitUntilReady(context, singleplayer, uuid);
		}

		LangMod.LOGGER.info("LANGMOD-GAMETEST: all checks passed");
	}

	@SuppressWarnings("unchecked")
	private static void toggle(OptionInstance<?> option) {
		OptionInstance<Boolean> toggle = (OptionInstance<Boolean>) option;
		toggle.set(!toggle.get());
	}

	/** The two buttons LangMod adds to the game menu are inside the screen and overlap no other button. */
	private static boolean menuButtonsFit(Screen screen) {
		List<AbstractWidget> widgets = Screens.getWidgets(screen).stream().filter(widget -> widget.visible).toList();
		List<AbstractWidget> ours = widgets.stream()
				.filter(widget -> hasKey(widget, GameMenuButtons.ITEMS) || hasKey(widget, GameMenuButtons.LANGUAGE)).toList();
		return ours.size() == 2 && fitWithoutOverlap(ours, widgets, screen);
	}

	/** Every widget is inside the screen and overlaps none of the others. */
	private static boolean fitWithoutOverlap(List<AbstractWidget> widgets, Screen screen) {
		return fitWithoutOverlap(widgets, widgets, screen);
	}

	/** Each of {@code ours} is inside the screen and overlaps none of {@code widgets}. */
	private static boolean fitWithoutOverlap(List<AbstractWidget> ours, List<AbstractWidget> widgets, Screen screen) {
		for (AbstractWidget a : ours) {
			if (a.getX() < 0 || a.getY() < 0 || a.getX() + a.getWidth() > screen.width || a.getY() + a.getHeight() > screen.height) {
				return false;
			}

			for (AbstractWidget b : widgets) {
				if (a != b && a.getX() < b.getX() + b.getWidth() && b.getX() < a.getX() + a.getWidth()
						&& a.getY() < b.getY() + b.getHeight() && b.getY() < a.getY() + a.getHeight()) {
					return false;
				}
			}
		}

		return true;
	}

	private static boolean hasKey(AbstractWidget widget, String key) {
		return widget.getMessage().getContents() instanceof TranslatableContents contents && contents.getKey().equals(key);
	}

	@SuppressWarnings("unchecked")
	private static <T> void select(OptionInstance<?> option, T value) {
		((OptionInstance<T>) option).set(value);
	}

	/** Runs a command as the player and returns its result. */
	private static int command(TestSingleplayerContext singleplayer, UUID uuid, String command) {
		return singleplayer.getServer().computeOnServer(server -> {
			ServerPlayer player = server.getPlayerList().getPlayer(uuid);

			try {
				return server.getCommands().getDispatcher().execute(command, player.createCommandSourceStack());
			} catch (CommandSyntaxException e) {
				throw new AssertionError("/" + command + " failed: " + e.getMessage(), e);
			}
		});
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
		String shown = context.computeOnClient(minecraft -> ClientChallenge.get().active().entry().text());
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

	/** Switches language as the menu does, and waits until the server has heard about it. */
	private static void selectLanguage(ClientGameTestContext context, TestSingleplayerContext singleplayer, UUID uuid, Language language) {
		context.runOnClient(minecraft -> LangModClient.selectLanguage(language));
		waitUntil(context, "the server to switch to " + language.id(),
				() -> singleplayer.getServer().computeOnServer(server -> ChallengeManager.get().language(uuid) == language));
	}

	private static PlayerLangData.Progress mandarin(TestSingleplayerContext singleplayer, UUID uuid) {
		return data(singleplayer, uuid).progress(Language.MANDARIN);
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
