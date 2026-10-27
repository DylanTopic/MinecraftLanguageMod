package io.github.dylantopic.langmod.command;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.item.Item;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import io.github.dylantopic.langmod.LangModConfig;
import io.github.dylantopic.langmod.ModResources;
import io.github.dylantopic.langmod.challenge.ChallengeManager;
import io.github.dylantopic.langmod.core.PinyinEntry;
import io.github.dylantopic.langmod.data.PlayerLangData;
import io.github.dylantopic.langmod.net.ClientActionPayload;

/**
 * {@code /langmod ...}. All subcommands are server commands (Fabric cannot mix client and server
 * subcommands under one root); the ones that need the microphone ask the client to do the work.
 */
public final class LangModCommands {
	private static final int PAGE_SIZE = 10;

	private LangModCommands() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
				Commands.literal("langmod")
						.then(Commands.literal("test").executes(context -> clientAction(context, ClientActionPayload.Action.MIC_TEST)))
						.then(Commands.literal("mics").executes(context -> clientAction(context, ClientActionPayload.Action.LIST_MICS)))
						.then(Commands.literal("debug").executes(context -> clientAction(context, ClientActionPayload.Action.TOGGLE_DEBUG)))
						.then(Commands.literal("config").executes(context -> clientAction(context, ClientActionPayload.Action.OPEN_CONFIG)))
						.then(Commands.literal("list")
								.executes(context -> list(context, 1))
								.then(Commands.argument("page", IntegerArgumentType.integer(1))
										.executes(context -> list(context, IntegerArgumentType.getInteger(context, "page")))))
						.then(Commands.literal("reset")
								.executes(context -> reset(context, false))
								.then(Commands.literal("everything").executes(context -> reset(context, true))))
						.then(Commands.literal("review").executes(LangModCommands::review))
						.then(Commands.literal("reload").executes(LangModCommands::reload))
		));
	}

	private static int list(CommandContext<CommandSourceStack> context, int page) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		List<String> learned = new ArrayList<>(data(player).learned());

		if (learned.isEmpty()) {
			context.getSource().sendSuccess(() -> Component.literal("You have not learned any items yet."), false);
			return 0;
		}

		int pages = (learned.size() + PAGE_SIZE - 1) / PAGE_SIZE;
		int shownPage = Math.min(page, pages);
		context.getSource().sendSuccess(() -> Component.literal("Learned items (" + learned.size() + "), page " + shownPage + "/" + pages
				+ (pages > 1 ? " - /langmod list <page>" : "") + ":").withStyle(ChatFormatting.GOLD), false);

		for (int i = (shownPage - 1) * PAGE_SIZE; i < Math.min(learned.size(), shownPage * PAGE_SIZE); i++) {
			int number = i + 1;
			MutableComponent line = describe(learned.get(i));
			context.getSource().sendSuccess(() -> Component.literal(number + ". ").withStyle(ChatFormatting.GRAY).append(line), false);
		}

		return learned.size();
	}

	/** Forgets the learned items (and with {@code everything}, the mistake history used by /langmod review). */
	private static int reset(CommandContext<CommandSourceStack> context, boolean everything) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		PlayerLangData data = data(player);
		int count = data.learned().size();
		player.setAttached(PlayerLangData.TYPE, everything ? data.withProgressCleared() : data.withLearnedCleared());
		context.getSource().sendSuccess(() -> Component.literal("Forgot " + count + " learned item" + (count == 1 ? "" : "s")
				+ (everything ? " and your mistake history" : "") + ". Every item will be challenged again on its next pickup."), false);
		return count;
	}

	private static int review(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		PlayerLangData data = data(player);
		Set<String> ids = new HashSet<>(data.failures().keySet());
		ids.addAll(data.wrongAttempts().keySet());

		if (ids.isEmpty()) {
			context.getSource().sendSuccess(() -> Component.literal("No mistakes recorded yet."), false);
			return 0;
		}

		List<String> sorted = new ArrayList<>(ids);
		sorted.sort(Comparator.<String>comparingInt(id -> data.failures().getOrDefault(id, 0)).reversed()
				.thenComparing(Comparator.<String>comparingInt(id -> data.wrongAttempts().getOrDefault(id, 0)).reversed()));
		context.getSource().sendSuccess(() -> Component.literal("Items you struggled with most:").withStyle(ChatFormatting.GOLD), false);

		for (String id : sorted.subList(0, Math.min(PAGE_SIZE, sorted.size()))) {
			int failed = data.failures().getOrDefault(id, 0);
			int wrong = data.wrongAttempts().getOrDefault(id, 0);
			MutableComponent line = describe(id)
					.append(Component.literal("  failed " + failed + "x, " + wrong + " wrong answer" + (wrong == 1 ? "" : "s"))
							.withStyle(ChatFormatting.RED))
					.append(data.isLearned(id) ? Component.literal("  (learned since)").withStyle(ChatFormatting.GREEN) : Component.empty());
			context.getSource().sendSuccess(() -> line, false);
		}

		return sorted.size();
	}

	private static int reload(CommandContext<CommandSourceStack> context) {
		// Checked here rather than in requires(): the command tree is also built for sources without a server.
		if (context.getSource().getServer().isDedicatedServer() && !context.getSource().permissions().hasPermission(Permissions.COMMANDS_MODERATOR)) {
			context.getSource().sendFailure(Component.literal("Only operators can reload LangMod's settings."));
			return 0;
		}

		LangModConfig.load();
		ChallengeManager.get().setDictionary(ModResources.loadItemDictionary());
		context.getSource().sendSuccess(() -> Component.literal("Reloaded config/langmod/langmod.json and pinyin_overrides.json ("
				+ ChallengeManager.get().dictionary().overrideCount() + " overrides)."), false);
		ServerPlayer player = context.getSource().getPlayer();

		if (player != null && ServerPlayNetworking.canSend(player, ClientActionPayload.TYPE)) {
			ServerPlayNetworking.send(player, new ClientActionPayload(ClientActionPayload.Action.RELOAD));
		}

		return 1;
	}

	/** "苹果 píng guǒ (Apple)" for an item id; the English name is translated on the player's client. */
	public static MutableComponent describe(String itemId) {
		Identifier id = Identifier.tryParse(itemId);
		Optional<Item> item = id == null ? Optional.empty() : BuiltInRegistries.ITEM.getOptional(id);

		if (item.isEmpty()) {
			return Component.literal(itemId).withStyle(ChatFormatting.WHITE); // e.g. an item from a mod that was removed
		}

		String key = item.get().getDescriptionId();
		Optional<PinyinEntry> entry = ChallengeManager.get().dictionary().get(key);
		MutableComponent line = entry
				.map(e -> Component.literal(e.hanzi()).withStyle(ChatFormatting.WHITE)
						.append(Component.literal(" " + e.marks()).withStyle(ChatFormatting.AQUA)))
				.orElseGet(() -> Component.literal(itemId).withStyle(ChatFormatting.WHITE));
		return line.append(Component.literal(" (").withStyle(ChatFormatting.GRAY))
				.append(Component.translatable(key).withStyle(ChatFormatting.GRAY))
				.append(Component.literal(")").withStyle(ChatFormatting.GRAY));
	}

	private static PlayerLangData data(ServerPlayer player) {
		return player.getAttachedOrCreate(PlayerLangData.TYPE);
	}

	private static int clientAction(CommandContext<CommandSourceStack> context, ClientActionPayload.Action action) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();

		if (!ServerPlayNetworking.canSend(player, ClientActionPayload.TYPE)) {
			context.getSource().sendFailure(Component.literal("LangMod is not installed on your client."));
			return 0;
		}

		ServerPlayNetworking.send(player, new ClientActionPayload(action));
		return 1;
	}
}
