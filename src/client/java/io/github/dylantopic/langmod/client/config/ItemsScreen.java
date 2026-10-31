package io.github.dylantopic.langmod.client.config;

import java.text.Collator;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.mojang.blaze3d.platform.cursor.CursorTypes;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import io.github.dylantopic.langmod.LangModConfig;
import io.github.dylantopic.langmod.LanguagePacks;
import io.github.dylantopic.langmod.core.lang.JapaneseRomanizer;
import io.github.dylantopic.langmod.core.lang.Language;
import io.github.dylantopic.langmod.core.lang.LanguagePack;
import io.github.dylantopic.langmod.net.LearnedItemsPayload;
import io.github.dylantopic.langmod.net.LearnedItemsRequestPayload;

/**
 * Every item that has a name in the language being learned, with its icon, in alphabetical order
 * (Mandarin by pinyin, Japanese by reading). Clicking an item turns it off: it is then picked up normally and never
 * challenged. All items are on by default. Learned items are marked. Opened from the game menu
 * (Esc) or with {@code /langmod items}.
 */
public class ItemsScreen extends Screen {
	private static final int ROW_HEIGHT = 28;
	private static final int LIST_TOP = 48;
	private static final int ICON = 20;
	private static final int SQUARE = 14;
	private static final float NAME_SCALE = 2.0f;
	private static final int ON = 0xFF3CC83C;
	private static final int OFF = 0xFFC83232;
	private static final int SKIPPED = 0xFF6E6E6E;
	private static final int LEARNED = 0xFF8FD48F;
	private static final int DIM = 0xFFA0A0A8;

	private final Screen parent;
	private final Language language;
	private final List<Row> rows;
	private Set<String> learned = Set.of();
	private boolean learnedRequested;
	private boolean learnedLoaded;
	private int learnedCount;
	private int offCount;
	private List<FormattedCharSequence> legend = List.of();
	private ItemList list;
	private EditBox search;

	/**
	 * @param itemId e.g. {@code minecraft:apple}
	 * @param search the name, its pronunciation, the name in the game's language and the id, folded for searching
	 * @param longName never challenged because of the long-name setting
	 */
	private record Row(String itemId, ItemStack stack, String name, String sortKey, String search, String compactSearch, boolean longName) {
	}

	/** @param parent screen to return to, or null to go back to the game */
	public ItemsScreen(Screen parent) {
		super(Component.translatable("langmod.items.title"));
		this.parent = parent;
		this.language = LangModConfig.get().languageEnum();
		this.rows = rows(language);
		updateCounts();
	}

	@Override
	protected void init() {
		legend = this.font.split(Component.translatable("langmod.items.legend"), this.width - 40);
		int listBottom = this.height - 32 - legend.size() * lineHeight();
		list = new ItemList(this.minecraft, this.width, listBottom - LIST_TOP, LIST_TOP);

		int searchWidth = Math.min(300, this.width - 40);
		search = new EditBox(this.font, (this.width - searchWidth) / 2, 10, searchWidth, 20, search, Component.translatable("langmod.items.search"));
		search.setHint(Component.translatable("langmod.items.search"));
		search.setResponder(text -> refresh());

		this.addRenderableWidget(search);
		this.addRenderableWidget(list);
		this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
				.pos(this.width / 2 - 100, this.height - 26).size(200, 20).build());
		this.setInitialFocus(search);
		refresh();

		if (!learnedRequested && ClientPlayNetworking.canSend(LearnedItemsRequestPayload.TYPE)) {
			ClientPlayNetworking.send(new LearnedItemsRequestPayload(language.id()));
			learnedRequested = true;
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		graphics.centeredText(this.font, Component.translatable("langmod.items.header", language.englishName(), learnedCount, rows.size(), offCount),
				this.width / 2, 36, DIM);

		if (list.children().isEmpty()) {
			graphics.centeredText(this.font, Component.translatable("langmod.items.empty"), this.width / 2, LIST_TOP + 20, DIM);
		}

		int y = list.getY() + list.getHeight() + 4;

		for (FormattedCharSequence line : legend) {
			graphics.text(this.font, line, (this.width - this.font.width(line)) / 2, y, DIM);
			y += lineHeight();
		}
	}

	@Override
	public void onClose() {
		this.minecraft.setScreen(parent);
	}

	/** The server's answer to {@link LearnedItemsRequestPayload}. */
	public void onLearnedItems(LearnedItemsPayload payload) {
		if (payload.language().equals(language.id())) {
			learned = Set.copyOf(payload.itemIds());
			learnedLoaded = true;
			updateCounts();
		}
	}

	/** Whether the server has said which items are learned (for the game test). */
	public boolean learnedLoaded() {
		return learnedLoaded;
	}

	/** Listed items the player has learned in this language. */
	public int learnedCount() {
		return learnedCount;
	}

	/** Turns an item on or off for the language being learned. */
	public void toggle(String itemId) {
		boolean enabled = LangModConfig.get().isItemEnabled(language, itemId);
		LangModConfig.update(config -> config.setItemEnabled(language, itemId, !enabled));
		updateCounts();
	}

	/** Ids of the items the list shows now, in order (used by the game test). */
	public List<String> shownItemIds() {
		return list.children().stream().map(entry -> entry.row.itemId()).toList();
	}

	private void refresh() {
		String query = fold(search.getValue().trim());
		String compact = query.replace(" ", "");
		list.show(query.isEmpty() ? rows
				: rows.stream().filter(row -> row.search().contains(query) || row.compactSearch().contains(compact)).toList());
	}

	private void updateCounts() {
		LangModConfig config = LangModConfig.get();
		learnedCount = (int) rows.stream().filter(row -> learned.contains(row.itemId())).count();
		offCount = (int) rows.stream().filter(row -> !config.isItemEnabled(language, row.itemId())).count();
	}

	private int lineHeight() {
		return this.font.lineHeight + 1;
	}

	/** Every item with a name in the language, in the order shown. */
	private static List<Row> rows(Language language) {
		LangModConfig config = LangModConfig.get();
		LanguagePack pack = LanguagePacks.get(language);
		List<Row> rows = new ArrayList<>();

		for (Item item : BuiltInRegistries.ITEM) {
			if (item == Items.AIR) {
				continue;
			}

			String key = item.getDescriptionId();
			pack.name(key).ifPresent(entry -> {
				String id = BuiltInRegistries.ITEM.getKey(item).toString();
				String gameName = Component.translatable(key).getString();
				String search = fold(String.join("\n", entry.text(), entry.pronunciation(), gameName, id));
				boolean longName = language.length(entry.text()) > config.maxNameLength(language) && config.skipLongNames(language);
				rows.add(new Row(id, new ItemStack(item), entry.text(), pack.sortKey(entry), search, search.replace(" ", ""), longName));
			});
		}

		Collator collator = Collator.getInstance(Locale.forLanguageTag(language.id().replace('_', '-')));
		rows.sort(Comparator.comparing(Row::sortKey, collator).thenComparing(Row::itemId));
		return rows;
	}

	/**
	 * Lowercase without accents or tone marks, and katakana as hiragana, so "pina" finds piña,
	 * "pingguo" finds píng guǒ and "りんご" finds リンゴ.
	 */
	private static String fold(String text) {
		String plain = Normalizer.normalize(JapaneseRomanizer.toHiragana(text).toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
		return Normalizer.normalize(plain, Normalizer.Form.NFC);
	}

	private final class ItemList extends ObjectSelectionList<ItemEntry> {
		ItemList(Minecraft minecraft, int width, int height, int y) {
			super(minecraft, width, height, y, ROW_HEIGHT);
		}

		@Override
		public int getRowWidth() {
			return Math.min(360, this.width - 50);
		}

		void show(List<Row> shown) {
			clearEntries();
			shown.forEach(row -> addEntry(new ItemEntry(row)));
			setScrollAmount(0);
		}
	}

	private final class ItemEntry extends ObjectSelectionList.Entry<ItemEntry> {
		private final Row row;

		ItemEntry(Row row) {
			this.row = row;
		}

		@Override
		public Component getNarration() {
			return Component.literal(row.name());
		}

		@Override
		public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float delta) {
			int x = getContentX();
			int right = x + getContentWidth();
			int middle = getContentY() + getContentHeight() / 2;
			boolean on = LangModConfig.get().isItemEnabled(language, row.itemId());

			graphics.pose().pushMatrix();
			graphics.pose().translate(x, middle - ICON / 2.0f);
			graphics.pose().scale(ICON / 16.0f);
			graphics.item(row.stack(), 0, 0);
			graphics.pose().popMatrix();

			// The on/off square: green = challenged when picked up, red = picked up normally, grey = long name (skipped).
			int squareX = right - SQUARE - 2;
			int squareY = middle - SQUARE / 2;
			graphics.fill(squareX, squareY, squareX + SQUARE, squareY + SQUARE, row.longName() ? SKIPPED : on ? ON : OFF);
			graphics.outline(squareX - 1, squareY - 1, SQUARE + 2, SQUARE + 2, hovered && !row.longName() ? 0xFFFFFFFF : 0xFF000000);

			int textRight = squareX - 8;
			String tag = row.longName() ? Component.translatable("langmod.items.long").getString()
					: learned.contains(row.itemId()) ? Component.translatable("langmod.items.learned").getString() : "";

			if (!tag.isEmpty()) {
				int tagX = textRight - font.width(tag);
				graphics.text(font, tag, tagX, middle - font.lineHeight / 2, row.longName() ? SKIPPED : LEARNED, false);
				textRight = tagX - 8;
			}

			int textX = x + ICON + 8;
			drawName(graphics, row.name(), textX, textRight - textX, middle, on || row.longName() ? 0xFFFFFFFF : DIM);

			if (hovered && !row.longName()) {
				graphics.requestCursor(CursorTypes.POINTING_HAND);
			}
		}

		@Override
		public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
			if (row.longName()) {
				return false;
			}

			toggle(row.itemId());
			return true;
		}

		/** The name, as large as fits (at least normal size, cut off with "..." beyond that). */
		private void drawName(GuiGraphicsExtractor graphics, String name, int x, int maxWidth, int middle, int color) {
			int width = font.width(name);
			float scale = Math.max(1.0f, Math.min(NAME_SCALE, maxWidth / (float) Math.max(1, width)));
			String shown = name;

			if (width > maxWidth) {
				while (shown.length() > 1 && font.width(shown + "...") > maxWidth) {
					shown = shown.substring(0, shown.length() - 1);
				}

				shown += "...";
			}

			graphics.pose().pushMatrix();
			graphics.pose().translate(x, middle - font.lineHeight * scale / 2);
			graphics.pose().scale(scale);
			graphics.text(font, shown, 0, 0, color, true);
			graphics.pose().popMatrix();
		}
	}
}
