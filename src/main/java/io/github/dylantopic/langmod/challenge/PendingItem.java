package io.github.dylantopic.langmod.challenge;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.item.ItemStack;

import io.github.dylantopic.langmod.LangModConfig;
import io.github.dylantopic.langmod.core.lang.Language;
import io.github.dylantopic.langmod.core.lang.NameEntry;

/**
 * Items of one type that were picked up but are held back until the player says the name.
 * Further pickups of the same type are merged in while it waits.
 */
final class PendingItem {
	final String itemId;
	final Language language;
	final NameEntry entry;
	final boolean longName;
	final List<ItemStack> stacks = new ArrayList<>();

	PendingItem(String itemId, Language language, NameEntry entry, boolean longName) {
		this.itemId = itemId;
		this.language = language;
		this.entry = entry;
		this.longName = longName;
	}

	/** "苹果  píng guǒ", "Manzana" or "사과  sagwa"; only the name when the pronunciation is turned off. */
	String nameWithPronunciation() {
		boolean show = LangModConfig.get().showPronunciation && !entry.pronunciation().isEmpty();
		return show ? entry.text() + "  " + entry.pronunciation() : entry.text();
	}

	/** A stack for the popup icon (count 1, keeps components such as potion colour). */
	ItemStack displayStack() {
		if (stacks.isEmpty()) {
			return ItemStack.EMPTY;
		}

		ItemStack copy = stacks.getFirst().copy();
		copy.setCount(1);
		return copy;
	}
}
