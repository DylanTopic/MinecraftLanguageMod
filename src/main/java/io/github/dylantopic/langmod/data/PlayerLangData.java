package io.github.dylantopic.langmod.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.world.item.ItemStack;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

import io.github.dylantopic.langmod.LangMod;

/**
 * Per-player, per-world progress, stored in the player's save data via a Fabric data attachment
 * (so it lives in {@code <world>/playerdata/<uuid>.dat} and survives death).
 *
 * @param learned item ids (e.g. {@code minecraft:apple}) the player has said correctly, in the order learned
 * @param failures item id -> number of challenges failed (item disappeared)
 * @param wrongAttempts item id -> number of wrong answers
 * @param pending items held while a challenge is running or queued. Normally empty: they are given back
 *     when the player leaves, and this copy only matters if the game crashes mid-challenge.
 */
public record PlayerLangData(Set<String> learned, Map<String, Integer> failures, Map<String, Integer> wrongAttempts,
		List<ItemStack> pending) {
	public static final PlayerLangData EMPTY = new PlayerLangData(Set.of(), Map.of(), Map.of(), List.of());

	public static final Codec<PlayerLangData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.listOf()
					.<Set<String>>xmap(list -> Collections.unmodifiableSet(new LinkedHashSet<>(list)), List::copyOf)
					.optionalFieldOf("learned", Set.of())
					.forGetter(PlayerLangData::learned),
			Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("failures", Map.of()).forGetter(PlayerLangData::failures),
			Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("wrong_attempts", Map.of()).forGetter(PlayerLangData::wrongAttempts),
			ItemStack.CODEC.listOf().optionalFieldOf("pending", List.of()).forGetter(PlayerLangData::pending)
	).apply(instance, PlayerLangData::new));

	public static final AttachmentType<PlayerLangData> TYPE = AttachmentRegistry.create(
			LangMod.id("player_data"),
			builder -> builder
					.initializer(() -> EMPTY)
					.persistent(CODEC)
					.copyOnDeath()
	);

	public PlayerLangData {
		learned = Collections.unmodifiableSet(new LinkedHashSet<>(learned));
		failures = Map.copyOf(failures);
		wrongAttempts = Map.copyOf(wrongAttempts);
		pending = pending.stream().filter(stack -> !stack.isEmpty()).map(ItemStack::copy).toList();
	}

	/** Forces class loading so the attachment type is registered during mod initialisation. */
	public static void init() {
	}

	public boolean isLearned(String itemId) {
		return learned.contains(itemId);
	}

	public PlayerLangData withLearned(String itemId) {
		Set<String> set = new LinkedHashSet<>(learned);
		set.add(itemId);
		return new PlayerLangData(set, failures, wrongAttempts, pending);
	}

	public PlayerLangData withLearnedCleared() {
		return new PlayerLangData(Set.of(), failures, wrongAttempts, pending);
	}

	public PlayerLangData withFailure(String itemId) {
		Map<String, Integer> map = new HashMap<>(failures);
		map.merge(itemId, 1, Integer::sum);
		return new PlayerLangData(learned, map, wrongAttempts, pending);
	}

	public PlayerLangData withWrongAttempt(String itemId) {
		Map<String, Integer> map = new HashMap<>(wrongAttempts);
		map.merge(itemId, 1, Integer::sum);
		return new PlayerLangData(learned, failures, map, pending);
	}

	public PlayerLangData withPending(List<ItemStack> stacks) {
		return new PlayerLangData(learned, failures, wrongAttempts, new ArrayList<>(stacks));
	}
}
