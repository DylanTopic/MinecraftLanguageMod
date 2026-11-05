package io.github.dylantopic.langmod.client.villager;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import io.github.dylantopic.langmod.core.lang.Language;
import io.github.dylantopic.langmod.net.VillagerActionPayload;

/** Shown when right-clicking a villager: Trade opens the normal trading screen, Conversate a conversation. */
public class VillagerChoiceScreen extends Screen {
	public static final String TRADE = "langmod.villager.trade";
	public static final String TALK = "langmod.villager.talk";
	private static final int BUTTON_WIDTH = 140;

	private final int villagerId;
	private final boolean canTrade;
	private final Language language;

	public VillagerChoiceScreen(int villagerId, boolean canTrade, Language language) {
		super(Villagers.name(villagerId));
		this.villagerId = villagerId;
		this.canTrade = canTrade;
		this.language = language;
	}

	@Override
	protected void init() {
		int y = this.height / 2 - 10;
		Button trade = Button.builder(Component.translatable(TRADE), button -> trade())
				.pos(this.width / 2 - BUTTON_WIDTH - 4, y).size(BUTTON_WIDTH, 20).build();
		trade.active = canTrade;

		if (!canTrade) {
			trade.setTooltip(Tooltip.create(Component.translatable("langmod.villager.noTrades")));
		}

		Button talk = Button.builder(Component.translatable(TALK), button -> talk())
				.pos(this.width / 2 + 4, y).size(BUTTON_WIDTH, 20)
				.tooltip(Tooltip.create(Component.translatable("langmod.villager.talk.tooltip", language.englishName()))).build();
		this.addRenderableWidget(trade);
		this.addRenderableWidget(talk);
		this.setInitialFocus(talk);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		graphics.centeredText(this.font, this.title, this.width / 2, this.height / 2 - 46, 0xFFFFFFFF);
		graphics.centeredText(this.font, Component.translatable("langmod.villager.question"), this.width / 2, this.height / 2 - 30, 0xFFC8C8D0);
		graphics.centeredText(this.font, Component.translatable("langmod.villager.sneak"), this.width / 2, this.height / 2 + 22, 0xFF8C8C96);
	}

	@Override
	public void tick() {
		super.tick();

		if (!Villagers.near(villagerId)) {
			onClose();
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	public int villagerId() {
		return villagerId;
	}

	private void trade() {
		Villagers.send(new VillagerActionPayload(villagerId, VillagerActionPayload.Action.TRADE));
		onClose(); // the server opens the trading screen
	}

	private void talk() {
		Villagers.send(new VillagerActionPayload(villagerId, VillagerActionPayload.Action.TALK));
		this.minecraft.setScreen(new VillagerConversationScreen(villagerId, language));
	}
}
