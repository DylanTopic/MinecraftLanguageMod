package io.github.dylantopic.langmod.challenge;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import io.github.dylantopic.langmod.LangMod;
import io.github.dylantopic.langmod.LangModConfig;
import io.github.dylantopic.langmod.LanguagePacks;
import io.github.dylantopic.langmod.core.lang.CheckResult;
import io.github.dylantopic.langmod.core.lang.Language;
import io.github.dylantopic.langmod.core.lang.LanguagePack;
import io.github.dylantopic.langmod.core.lang.NameEntry;
import io.github.dylantopic.langmod.data.PlayerLangData;
import io.github.dylantopic.langmod.net.AttemptResultPayload;
import io.github.dylantopic.langmod.net.ChallengeEndPayload;
import io.github.dylantopic.langmod.net.ChallengeStartPayload;
import io.github.dylantopic.langmod.net.ChallengeStatePayload;
import io.github.dylantopic.langmod.net.ClientHelloPayload;
import io.github.dylantopic.langmod.net.ClientPhasePayload;
import io.github.dylantopic.langmod.net.LangModNetworking;
import io.github.dylantopic.langmod.net.TranscriptionPayload;

/**
 * Server-side game logic: intercepts first pickups, holds the items, runs one challenge at a time
 * per player (others queue up) and hands the items out again.
 *
 * <p>Everything here runs on the server thread (pickups, ticks, packet handlers, events).
 */
public final class ChallengeManager {
	private static final ChallengeManager INSTANCE = new ChallengeManager();
	/** Pause between two challenges so the result of the previous one can be read. */
	private static final int COOLDOWN_TICKS = 30;
	/** If the client reports "speaking"/"checking" and then goes quiet this long, resume the countdown. */
	private static final int PHASE_TIMEOUT_TICKS = 30 * 20;

	private final Map<UUID, PlayerState> players = new HashMap<>();
	private long tick;

	private ChallengeManager() {
	}

	public static ChallengeManager get() {
		return INSTANCE;
	}

	/** The language a player is learning (Mandarin until their client says otherwise). */
	public Language language(UUID player) {
		PlayerState state = players.get(player);
		return state == null ? Language.MANDARIN : state.language;
	}

	// ------------------------------------------------------------------------------------------
	// Pickup interception
	// ------------------------------------------------------------------------------------------

	/**
	 * Called at the start of {@code ItemEntity#playerTouch} when vanilla would otherwise pick the item
	 * up. Returns true if the item was taken into a pending challenge (vanilla pickup is cancelled).
	 */
	public boolean onPlayerTouchItem(ServerPlayer player, ItemEntity itemEntity) {
		LangModConfig config = LangModConfig.get();

		if (!config.enabled || player.isSpectator()) {
			return false;
		}

		PlayerState state = players.get(player.getUUID());

		if (state == null || !state.speechReady) {
			// No challenges without a working microphone + speech model on the other end.
			return false;
		}

		ItemStack stack = itemEntity.getItem();

		if (stack.isEmpty()) {
			return false;
		}

		String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
		Language language = state.language;

		if (data(player).isLearned(language, itemId)) {
			return false;
		}

		PendingItem pending = state.find(itemId);

		if (pending == null) {
			Optional<NameEntry> entry = LanguagePacks.get(language).name(stack.getItem().getDescriptionId());

			if (entry.isEmpty()) {
				return false; // no name known in this language (e.g. modded items): normal pickup
			}

			boolean longName = language.length(entry.get().text()) > config.maxNameLength(language);

			if (longName && config.skipLongNames(language)) {
				return false;
			}

			pending = new PendingItem(itemId, language, entry.get(), longName);
			state.queue.addLast(pending);
		}

		// Take the whole stack: play the normal pickup animation, then empty and remove the entity
		// (emptying it like vanilla's inventory insert does, so nobody else can pick it up this tick).
		pending.stacks.add(stack.copy());
		player.take(itemEntity, stack.getCount());
		stack.setCount(0);
		itemEntity.discard();
		savePending(player, state);

		if (state.active == null) {
			tryStartNext(player, state);
		} else {
			sendState(player, state);
		}

		return true;
	}

	// ------------------------------------------------------------------------------------------
	// Lifecycle
	// ------------------------------------------------------------------------------------------

	public void onJoin(ServerPlayer player) {
		players.put(player.getUUID(), new PlayerState());
		PlayerLangData data = data(player);

		if (!data.pending().isEmpty()) {
			// Left over from a crash mid-challenge: give the items back, nothing counted.
			LangMod.LOGGER.info("Returning {} item stack(s) held when {} last left", data.pending().size(), player.getName().getString());
			giveAll(player, data.pending());
			player.setAttached(PlayerLangData.TYPE, data(player).withPending(List.of()));
		}
	}

	/** The player is leaving (or the world is closing): give everything back before the player is saved. */
	public void onLeave(ServerPlayer player) {
		PlayerState state = players.remove(player.getUUID());

		if (state != null) {
			returnEverything(player, state, null);
		}
	}

	/** The player died: give the items back so they drop (or stay, with keepInventory) like the rest. */
	public void onDeath(ServerPlayer player) {
		PlayerState state = players.get(player.getUUID());

		if (state != null) {
			returnEverything(player, state, "You died, so the challenge was cancelled.");
		}
	}

	public void onServerStopping(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			onLeave(player);
		}

		players.clear();
	}

	public void onClientHello(ServerPlayer player, ClientHelloPayload hello) {
		PlayerState state = players.computeIfAbsent(player.getUUID(), uuid -> new PlayerState());
		boolean ready = hello.protocolVersion() == ClientHelloPayload.PROTOCOL_VERSION && hello.speechReady();
		Language language = Language.byId(hello.language()).orElse(Language.MANDARIN);
		LangMod.LOGGER.info("{}: speech recognition {} ({}), learning {}", player.getName().getString(), ready ? "ready" : "not ready",
				hello.speechInfo(), language.englishName());
		state.speechReady = ready;
		boolean busy = state.active != null || !state.queue.isEmpty();

		if (!ready && busy) {
			returnEverything(player, state, "Speech recognition stopped, so your items were given back.");
		} else if (language != state.language && busy) {
			returnEverything(player, state, "You switched to " + language.englishName() + ", so your items were given back.");
		}

		state.language = language;
	}

	public boolean isSpeechReady(UUID player) {
		PlayerState state = players.get(player);
		return state != null && state.speechReady;
	}

	public boolean hasActiveChallenge(UUID player) {
		PlayerState state = players.get(player);
		return state != null && state.active != null;
	}

	// ------------------------------------------------------------------------------------------
	// Ticking
	// ------------------------------------------------------------------------------------------

	public void tick(MinecraftServer server) {
		tick++;
		boolean enabled = LangModConfig.get().enabled;
		Iterator<Map.Entry<UUID, PlayerState>> it = players.entrySet().iterator();

		while (it.hasNext()) {
			Map.Entry<UUID, PlayerState> entry = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());

			if (player == null) {
				continue;
			}

			PlayerState state = entry.getValue();

			if (!enabled && (state.active != null || !state.queue.isEmpty())) {
				returnEverything(player, state, "LangMod was turned off, so your items were given back.");
				continue;
			}

			if (state.cooldown > 0) {
				state.cooldown--;
			}

			Challenge challenge = state.active;

			if (challenge == null) {
				tryStartNext(player, state);
				continue;
			}

			if (challenge.timerRunning()) {
				challenge.remainingTicks--;

				if (challenge.remainingTicks <= 0) {
					onTimeUp(player, state, challenge);
					continue;
				}
			} else if (tick - challenge.phaseSince > PHASE_TIMEOUT_TICKS) {
				LangMod.LOGGER.warn("No word from {}'s client for {}s, resuming the countdown", player.getName().getString(), PHASE_TIMEOUT_TICKS / 20);
				challenge.phase = Challenge.Phase.LISTENING;
				challenge.phaseSince = tick;
			}

			if (tick % 10 == 0) {
				sendState(player, state);
			}
		}
	}

	// ------------------------------------------------------------------------------------------
	// Challenge flow
	// ------------------------------------------------------------------------------------------

	private void tryStartNext(ServerPlayer player, PlayerState state) {
		if (state.active != null || state.cooldown > 0 || state.queue.isEmpty() || !state.speechReady) {
			return;
		}

		LangModConfig config = LangModConfig.get();
		PendingItem next = state.queue.pollFirst();
		Challenge challenge = new Challenge(++state.lastChallengeId, next, config.timerSeconds * 20, config.maxAttempts, tick);
		state.active = challenge;
		LangMod.LOGGER.debug("Challenge {} for {}: {} ({})", challenge.id, player.getName().getString(), next.entry.text(), next.itemId);
		LangModNetworking.send(player, new ChallengeStartPayload(challenge.id, next.displayStack(), next.entry.key(), next.language.id(),
				next.entry.text(), next.entry.pronunciation(), challenge.remainingTicks, challenge.maxAttempts, state.queue.size(),
				next.longName));
	}

	public void onClientPhase(ServerPlayer player, ClientPhasePayload payload) {
		PlayerState state = players.get(player.getUUID());

		if (state != null && state.active != null && state.active.id == payload.challengeId()) {
			Challenge.Phase phase = Challenge.Phase.values()[payload.phase().ordinal()];

			if (phase != state.active.phase) {
				state.active.phase = phase;
				state.active.phaseSince = tick;
				sendState(player, state);
			}
		}
	}

	/** Checks an answer. Failed or empty transcriptions never count as an attempt. */
	public void onTranscription(ServerPlayer player, TranscriptionPayload payload) {
		PlayerState state = players.get(player.getUUID());
		Challenge challenge = state == null ? null : state.active;

		if (challenge == null || challenge.id != payload.challengeId()) {
			return; // a late answer for a challenge that already ended
		}

		challenge.phase = Challenge.Phase.LISTENING;
		challenge.phaseSince = tick;
		Language language = challenge.item.language;
		LanguagePack pack = LanguagePacks.get(language);
		NameEntry expected = challenge.item.entry;

		if (!payload.ok()) {
			LangModNetworking.send(player, AttemptResultPayload.notCounted(challenge.id, AttemptResultPayload.Verdict.ERROR, "", "",
					payload.millis(), challenge.attemptsUsed, "Speech recognition failed (" + payload.text() + "). Not counted, try again."));
			sendState(player, state);
			return;
		}

		String heard = pack.normalize(payload.text());

		if (heard.isEmpty() || pack.isNoise(heard, expected)) {
			LangModNetworking.send(player, AttemptResultPayload.notCounted(challenge.id, AttemptResultPayload.Verdict.NO_SPEECH,
					payload.text(), heard, payload.millis(), challenge.attemptsUsed, "Didn't catch that. Not counted, try again."));
			sendState(player, state);
			return;
		}

		LangModConfig config = LangModConfig.get();
		CheckResult result = pack.check(expected, heard, config.matchModeEnum(), challenge.item.longName);
		LangMod.LOGGER.info("{} said '{}' -> '{}' [{}] for {} [{}] ({}): {} ({} error(s), {} allowed, {})", player.getName().getString(),
				payload.text(), heard, result.heardPronunciation(), expected.text(), expected.pronunciation(), language.id(),
				result.pass() ? "correct" : "wrong", result.errors(), result.allowedErrors(), result.mode().id());

		if (result.pass()) {
			LangModNetworking.send(player, AttemptResultPayload.of(challenge.id, AttemptResultPayload.Verdict.CORRECT, payload.text(),
					result, payload.millis(), challenge.attemptsUsed, ""));
			succeed(player, state, challenge);
			return;
		}

		challenge.attemptsUsed++;
		player.setAttached(PlayerLangData.TYPE, data(player).withWrongAttempt(language, challenge.item.itemId));
		int left = challenge.maxAttempts - challenge.attemptsUsed;
		LangModNetworking.send(player, AttemptResultPayload.of(challenge.id, AttemptResultPayload.Verdict.WRONG, payload.text(),
				result, payload.millis(), challenge.attemptsUsed, left > 0 ? left + (left == 1 ? " try left" : " tries left") : ""));

		if (left <= 0) {
			fail(player, state, challenge, "Out of attempts.");
		} else {
			sendState(player, state);
		}
	}

	private void onTimeUp(ServerPlayer player, PlayerState state, Challenge challenge) {
		fail(player, state, challenge, "Time is up.");
	}

	/** Correct answer: hand over the items, mark the type as learned, celebrate. */
	private void succeed(ServerPlayer player, PlayerState state, Challenge challenge) {
		finish(player, state, challenge, ChallengeEndPayload.Outcome.SUCCESS, challenge.item.nameWithPronunciation());
		player.setAttached(PlayerLangData.TYPE, data(player).withLearned(challenge.item.language, challenge.item.itemId));
		giveAll(player, challenge.item.stacks);
		savePending(player, state);
		player.level().playSound((Player) null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.2f);
		player.level().sendParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getY() + 1.0, player.getZ(), 12, 0.4, 0.5, 0.4, 0.0);
	}

	/** Out of attempts or time: the held items vanish with a poof. The type stays unlearned. */
	private void fail(ServerPlayer player, PlayerState state, Challenge challenge, String reason) {
		finish(player, state, challenge, ChallengeEndPayload.Outcome.FAILED, reason + " It was " + challenge.item.nameWithPronunciation());
		player.setAttached(PlayerLangData.TYPE, data(player).withFailure(challenge.item.language, challenge.item.itemId));
		savePending(player, state);
		player.level().playSound((Player) null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.7f, 1.4f);
		player.level().sendParticles(ParticleTypes.POOF, player.getX(), player.getY() + 1.0, player.getZ(), 20, 0.3, 0.4, 0.3, 0.02);
	}

	private void finish(ServerPlayer player, PlayerState state, Challenge challenge, ChallengeEndPayload.Outcome outcome, String detail) {
		state.active = null;
		state.cooldown = COOLDOWN_TICKS;
		LangModNetworking.send(player, new ChallengeEndPayload(challenge.id, outcome, detail));
	}

	/** Cancels the active challenge and empties the queue, giving every held item back. */
	private void returnEverything(ServerPlayer player, PlayerState state, String message) {
		List<ItemStack> stacks = new ArrayList<>();

		if (state.active != null) {
			stacks.addAll(state.active.item.stacks);
			finish(player, state, state.active, ChallengeEndPayload.Outcome.CANCELLED, message == null ? "" : message);
		}

		for (PendingItem pending : state.queue) {
			stacks.addAll(pending.stacks);
		}

		state.queue.clear();
		giveAll(player, stacks);
		savePending(player, state);
	}

	private void sendState(ServerPlayer player, PlayerState state) {
		Challenge c = state.active;

		if (c != null) {
			LangModNetworking.send(player, new ChallengeStatePayload(c.id, c.remainingTicks, c.timerRunning(), c.attemptsUsed, state.queue.size()));
		}
	}

	// ------------------------------------------------------------------------------------------
	// Items and saved data
	// ------------------------------------------------------------------------------------------

	/** Puts stacks into the inventory; whatever does not fit is dropped at the player's feet. */
	static void giveAll(ServerPlayer player, List<ItemStack> stacks) {
		for (ItemStack original : stacks) {
			ItemStack stack = original.copy();
			boolean added = player.getInventory().add(stack);

			if (!added || !stack.isEmpty()) {
				ItemEntity drop = new ItemEntity(player.level(), player.getX(), player.getY(), player.getZ(), stack, 0, 0, 0);
				drop.setPickUpDelay(0);
				player.level().addFreshEntity(drop);
			}
		}
	}

	/** Mirrors every held stack into the player's saved data, so a crash cannot lose them. */
	private static void savePending(ServerPlayer player, PlayerState state) {
		List<ItemStack> all = new ArrayList<>();

		if (state.active != null) {
			all.addAll(state.active.item.stacks);
		}

		for (PendingItem pending : state.queue) {
			all.addAll(pending.stacks);
		}

		player.setAttached(PlayerLangData.TYPE, data(player).withPending(all));
	}

	static PlayerLangData data(ServerPlayer player) {
		return player.getAttachedOrCreate(PlayerLangData.TYPE);
	}

	/** Challenge state of one online player. */
	private static final class PlayerState {
		final ArrayDeque<PendingItem> queue = new ArrayDeque<>();
		Challenge active;
		boolean speechReady;
		Language language = Language.MANDARIN;
		int cooldown;
		int lastChallengeId;

		PendingItem find(String itemId) {
			if (active != null && active.item.itemId.equals(itemId)) {
				return active.item;
			}

			for (PendingItem pending : queue) {
				if (pending.itemId.equals(itemId)) {
					return pending;
				}
			}

			return null;
		}
	}
}
