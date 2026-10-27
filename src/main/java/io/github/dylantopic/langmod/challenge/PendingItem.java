package io.github.dylantopic.langmod.challenge;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.item.ItemStack;

import io.github.dylantopic.langmod.core.PinyinEntry;

/**
 * Items of one type that were picked up but are held back until the player says the name.
 * Further pickups of the same type are merged in while it waits.
 */
final class PendingItem {
	final String itemId;
	final PinyinEntry entry;
	final boolean longName;
	final List<ItemStack> stacks = new ArrayList<>();

	PendingItem(String itemId, PinyinEntry entry, boolean longName) {
		this.itemId = itemId;
		this.entry = entry;
		this.longName = longName;
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
