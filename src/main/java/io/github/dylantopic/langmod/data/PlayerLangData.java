package io.github.dylantopic.langmod.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
import io.github.dylantopic.langmod.core.lang.Language;

/**
 * Per-player, per-world progress, stored in the player's save data via a Fabric data attachment
 * (so it lives in {@code <world>/playerdata/<uuid>.dat} and survives death).
 *
 * @param languages progress per language id ({@code zh_cn}, {@code es_mx}, ...)
 * @param pending items held while a challenge is running or queued. Normally empty: they are given back
 *     when the player leaves, and this copy only matters if the game crashes mid-challenge.
 */
public record PlayerLangData(Map<String, Progress> languages, List<ItemStack> pending) {
	public static final PlayerLangData EMPTY = new PlayerLangData(Map.of(), List.of());

	/**
	 * Progress in one language.
	 *
	 * @param learned item ids (e.g. {@code minecraft:apple}) the player has said correctly, in the order learned
	 * @param failures item id -> number of challenges failed (item disappeared)
	 * @param wrongAttempts item id -> number of wrong answers
	 */
	public record Progress(Set<String> learned, Map<String, Integer> failures, Map<String, Integer> wrongAttempts) {
		public static final Progress EMPTY = new Progress(Set.of(), Map.of(), Map.of());

		public static final Codec<Progress> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.STRING.listOf()
						.<Set<String>>xmap(list -> Collections.unmodifiableSet(new LinkedHashSet<>(list)), List::copyOf)
						.optionalFieldOf("learned", Set.of())
						.forGetter(Progress::learned),
				Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("failures", Map.of()).forGetter(Progress::failures),
				Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("wrong_attempts", Map.of()).forGetter(Progress::wrongAttempts)
		).apply(instance, Progress::new));

		public Progress {
			learned = Collections.unmodifiableSet(new LinkedHashSet<>(learned));
			failures = Map.copyOf(failures);
			wrongAttempts = Map.copyOf(wrongAttempts);
		}

		public boolean isLearned(String itemId) {
			return learned.contains(itemId);
		}

		public boolean isEmpty() {
			return learned.isEmpty() && failures.isEmpty() && wrongAttempts.isEmpty();
		}

		Progress withLearned(String itemId) {
			Set<String> set = new LinkedHashSet<>(learned);
			set.add(itemId);
			return new Progress(set, failures, wrongAttempts);
		}

		Progress withFailure(String itemId) {
			Map<String, Integer> map = new HashMap<>(failures);
			map.merge(itemId, 1, Integer::sum);
			return new Progress(learned, map, wrongAttempts);
		}

		Progress withWrongAttempt(String itemId) {
			Map<String, Integer> map = new HashMap<>(wrongAttempts);
			map.merge(itemId, 1, Integer::sum);
			return new Progress(learned, failures, map);
		}
	}

	/**
	 * Reads the current format and the one from before languages were added (top-level learned,
	 * failures and wrong_attempts, which were all Mandarin); always writes the current one.
	 */
	public static final Codec<PlayerLangData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.unboundedMap(Codec.STRING, Progress.CODEC).optionalFieldOf("languages", Map.of()).forGetter(PlayerLangData::languages),
			Codec.STRING.listOf().optionalFieldOf("learned", List.of()).forGetter(data -> List.of()),
			Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("failures", Map.of()).forGetter(data -> Map.of()),
			Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("wrong_attempts", Map.of()).forGetter(data -> Map.of()),
			ItemStack.CODEC.listOf().optionalFieldOf("pending", List.of()).forGetter(PlayerLangData::pending)
	).apply(instance, PlayerLangData::decode));

	public static final AttachmentType<PlayerLangData> TYPE = AttachmentRegistry.create(
			LangMod.id("player_data"),
			builder -> builder
					.initializer(() -> EMPTY)
					.persistent(CODEC)
					.copyOnDeath()
	);

	public PlayerLangData {
		Map<String, Progress> copy = new LinkedHashMap<>(languages);
		copy.values().removeIf(Progress::isEmpty);
		languages = Collections.unmodifiableMap(copy);
		pending = pending.stream().filter(stack -> !stack.isEmpty()).map(ItemStack::copy).toList();
	}

	/** Forces class loading so the attachment type is registered during mod initialisation. */
	public static void init() {
	}

	static PlayerLangData decode(Map<String, Progress> languages, List<String> oldLearned, Map<String, Integer> oldFailures,
			Map<String, Integer> oldWrongAttempts, List<ItemStack> pending) {
		Map<String, Progress> merged = new LinkedHashMap<>(languages);
		Progress old = new Progress(new LinkedHashSet<>(oldLearned), oldFailures, oldWrongAttempts);

		if (!old.isEmpty()) {
			merged.merge(Language.MANDARIN.id(), old, (current, legacy) -> current.isEmpty() ? legacy : current);
		}

		return new PlayerLangData(merged, pending);
	}

	public Progress progress(Language language) {
		return languages.getOrDefault(language.id(), Progress.EMPTY);
	}

	public boolean isLearned(Language language, String itemId) {
		return progress(language).isLearned(itemId);
	}

	public PlayerLangData withLearned(Language language, String itemId) {
		return with(language, progress(language).withLearned(itemId));
	}

	public PlayerLangData withFailure(Language language, String itemId) {
		return with(language, progress(language).withFailure(itemId));
	}

	public PlayerLangData withWrongAttempt(Language language, String itemId) {
		return with(language, progress(language).withWrongAttempt(itemId));
	}

	/** Learned items in one language forgotten; its mistake history kept. */
	public PlayerLangData withLearnedCleared(Language language) {
		Progress progress = progress(language);
		return with(language, new Progress(Set.of(), progress.failures(), progress.wrongAttempts()));
	}

	/** Learned items and mistake counts in one language cleared; held items are kept. */
	public PlayerLangData withProgressCleared(Language language) {
		return with(language, Progress.EMPTY);
	}

	public PlayerLangData withPending(List<ItemStack> stacks) {
		return new PlayerLangData(languages, new ArrayList<>(stacks));
	}

	private PlayerLangData with(Language language, Progress progress) {
		Map<String, Progress> map = new LinkedHashMap<>(languages);
		map.put(language.id(), progress);
		return new PlayerLangData(map, pending);
	}
}
