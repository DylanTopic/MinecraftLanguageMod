package io.github.dylantopic.langmod.villager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import io.github.dylantopic.langmod.LangMod;
import io.github.dylantopic.langmod.LangModConfig;
import io.github.dylantopic.langmod.challenge.ChallengeManager;
import io.github.dylantopic.langmod.core.lang.Language;
import io.github.dylantopic.langmod.core.talk.TalkContext;
import io.github.dylantopic.langmod.core.talk.TalkReply;
import io.github.dylantopic.langmod.core.talk.TalkState;
import io.github.dylantopic.langmod.core.talk.VillagerTalk;
import io.github.dylantopic.langmod.mixin.MobInvoker;
import io.github.dylantopic.langmod.net.LangModNetworking;
import io.github.dylantopic.langmod.net.VillagerActionPayload;
import io.github.dylantopic.langmod.net.VillagerChoicePayload;
import io.github.dylantopic.langmod.net.VillagerReplyPayload;
import io.github.dylantopic.langmod.net.VillagerSayPayload;

/**
 * Talking to villagers. Right-clicking a villager asks the player whether to trade or to talk,
 * if the player is learning a language villagers speak and has the mod; everyone else (and
 * sneaking, or holding a name tag, lead or villager spawn egg) gets the normal behaviour. During
 * a conversation the villager stands still and looks at the player, as when trading, and answers
 * what the player says ({@link VillagerTalk}) from what is really around it.
 *
 * <p>Everything here runs on the server thread.
 */
public final class VillagerConversations {
	private static final VillagerConversations INSTANCE = new VillagerConversations();
	/** As far away as a trading screen stays open. */
	private static final double MAX_DISTANCE_SQR = 8.0 * 8.0;
	/** How close an iron golem has to be to protect the villager. */
	private static final double GOLEM_RANGE = 32.0;

	private final Map<UUID, Conversation> conversations = new HashMap<>();
	private long tick;

	private record Conversation(int villagerId, Language language, TalkState state) {
	}

	private VillagerConversations() {
	}

	public static VillagerConversations get() {
		return INSTANCE;
	}

	/** Whether the player is talking to a villager (item challenges wait meanwhile). */
	public boolean isTalking(UUID player) {
		return conversations.containsKey(player);
	}

	/**
	 * Fabric's UseEntityCallback, on both sides: on the server, right-clicking a villager opens the
	 * choice between trading and talking instead of the trading screen, for players it applies to.
	 */
	public InteractionResult onUseEntity(Player player, Level level, InteractionHand hand, Entity entity, EntityHitResult hit) {
		if (!(player instanceof ServerPlayer serverPlayer) || !(entity instanceof Villager villager) || hand != InteractionHand.MAIN_HAND
				|| !offersChoice(serverPlayer, villager)) {
			return InteractionResult.PASS;
		}

		end(serverPlayer, false);
		boolean canTrade = !villager.isBaby() && !villager.getOffers().isEmpty();
		LangModNetworking.send(serverPlayer, new VillagerChoicePayload(villager.getId(), canTrade));
		return InteractionResult.SUCCESS;
	}

	private static boolean offersChoice(ServerPlayer player, Villager villager) {
		ItemStack held = player.getMainHandItem();
		ChallengeManager challenges = ChallengeManager.get();
		return LangModConfig.get().enabled && VillagerTalk.supports(challenges.language(player.getUUID()))
				&& ServerPlayNetworking.canSend(player, VillagerChoicePayload.TYPE) && !player.isShiftKeyDown() && !player.isSpectator()
				&& villager.isAlive() && !villager.isSleeping() && !villager.isTrading()
				&& !held.is(Items.NAME_TAG) && !held.is(Items.LEAD) && !held.is(Items.VILLAGER_SPAWN_EGG)
				&& !challenges.hasActiveChallenge(player.getUUID());
	}

	public void onAction(ServerPlayer player, VillagerActionPayload payload) {
		end(player, false);

		if (payload.action() == VillagerActionPayload.Action.STOP) {
			return;
		}

		Villager villager = villager(player, payload.villagerId());

		if (villager == null || villager.isTrading()) {
			LangModNetworking.send(player, VillagerReplyPayload.left(payload.villagerId()));
			return;
		}

		if (payload.action() == VillagerActionPayload.Action.TRADE) {
			// The villager's own right-click handling: the trading screen (or a shake of the head).
			((MobInvoker) villager).langmod$mobInteract(player, InteractionHand.MAIN_HAND);
			return;
		}

		Language language = ChallengeManager.get().language(player.getUUID());

		if (!VillagerTalk.supports(language)) {
			LangModNetworking.send(player, VillagerReplyPayload.left(payload.villagerId()));
			return;
		}

		conversations.put(player.getUUID(), new Conversation(villager.getId(), language, new TalkState()));
		villager.setTradingPlayer(player); // stands still and looks at the player, as when trading
		player.awardStat(Stats.TALKED_TO_VILLAGER);
		LangMod.LOGGER.debug("{} is talking to villager {} in {}", player.getName().getString(), villager.getId(), language.englishName());
	}

	public void onSay(ServerPlayer player, VillagerSayPayload payload) {
		Conversation conversation = conversations.get(player.getUUID());

		if (conversation == null || conversation.villagerId() != payload.villagerId()) {
			return;
		}

		Villager villager = villager(player, conversation.villagerId());

		if (villager == null) {
			end(player, true);
			return;
		}

		TalkReply reply = VillagerTalk.reply(conversation.language(), payload.text(), context(player, villager), conversation.state());

		if (reply.action() == TalkReply.Action.SELL_BREAD) {
			sellBread(player);
		}

		react(villager, reply.mood());
		LangMod.LOGGER.info("LANGMOD-VILLAGER {}: '{}' -> '{}'", player.getName().getString(), payload.text(), reply.text());
		LangModNetworking.send(player, VillagerReplyPayload.reply(villager.getId(), reply));
	}

	/** Ends conversations whose villager walked away, fell asleep or is gone. */
	public void tick(MinecraftServer server) {
		if (conversations.isEmpty() || ++tick % 10 != 0) {
			return;
		}

		for (UUID uuid : List.copyOf(conversations.keySet())) {
			ServerPlayer player = server.getPlayerList().getPlayer(uuid);

			if (player == null) {
				conversations.remove(uuid);
			} else if (villager(player, conversations.get(uuid).villagerId()) == null) {
				end(player, true);
			}
		}
	}

	public void onLeave(ServerPlayer player) {
		end(player, false);
	}

	public void onServerStopping(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			end(player, false);
		}

		conversations.clear();
	}

	private void end(ServerPlayer player, boolean tellClient) {
		Conversation conversation = conversations.remove(player.getUUID());

		if (conversation == null) {
			return;
		}

		if (player.level().getEntity(conversation.villagerId()) instanceof Villager villager && villager.getTradingPlayer() == player) {
			villager.setTradingPlayer(null);
		}

		if (tellClient) {
			LangModNetworking.send(player, VillagerReplyPayload.left(conversation.villagerId()));
		}
	}

	/** The villager with this id if it can be talked to: alive, awake and near the player. */
	private static Villager villager(ServerPlayer player, int id) {
		return player.level().getEntity(id) instanceof Villager villager && villager.isAlive() && !villager.isSleeping()
				&& player.distanceToSqr(villager) <= MAX_DISTANCE_SQR ? villager : null;
	}

	/** What the villager knows: its name and job, the weather and time where it is, a golem nearby, the player's emeralds. */
	private static TalkContext context(ServerPlayer player, Villager villager) {
		ServerLevel level = (ServerLevel) villager.level();
		String name = villager.hasCustomName() && villager.getCustomName() != null ? villager.getCustomName().getString() : null;
		String profession = villager.getVillagerData().profession().unwrapKey().map(key -> key.identifier().getPath()).orElse("none");
		boolean raining = level.isRainingAt(villager.blockPosition().above()) || level.isRainingAt(player.blockPosition().above());
		TalkContext.Weather weather = !raining ? TalkContext.Weather.CLEAR : level.isThundering() ? TalkContext.Weather.THUNDER : TalkContext.Weather.RAIN;
		long dayTime = level.dimensionType().defaultClock().map(clock -> level.getServer().clockManager().getTotalTicks(clock)).orElse(6_000L);
		boolean golem = !level.getEntities(EntityType.IRON_GOLEM, villager.getBoundingBox().inflate(GOLEM_RANGE), Entity::isAlive).isEmpty();
		int emeralds = player.getInventory().countItem(Items.EMERALD);
		return new TalkContext(player.getName().getString(), name, profession, villager.isBaby(), weather, dayTime, golem, emeralds);
	}

	/** One emerald for one bread, the price the villager named. */
	private static void sellBread(ServerPlayer player) {
		Inventory inventory = player.getInventory();

		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);

			if (stack.is(Items.EMERALD)) {
				stack.shrink(1);
				break;
			}
		}

		ItemStack bread = new ItemStack(Items.BREAD);

		if (!inventory.add(bread)) {
			player.drop(bread, false);
		}

		inventory.setChanged();
	}

	/** The villager's "hmm" as it answers; it nods when pleased and shakes its head when it cannot help. */
	private static void react(Villager villager, TalkReply.Mood mood) {
		SoundEvent sound = switch (mood) {
			case TALK -> SoundEvents.VILLAGER_TRADE;
			case YES -> SoundEvents.VILLAGER_YES;
			case NO -> SoundEvents.VILLAGER_NO;
		};
		villager.level().playSound((Player) null, villager.getX(), villager.getY(), villager.getZ(), sound, SoundSource.NEUTRAL, 1.0f, villager.getVoicePitch());

		if (mood == TalkReply.Mood.NO) {
			villager.setUnhappyCounter(40);
		}
	}
}
