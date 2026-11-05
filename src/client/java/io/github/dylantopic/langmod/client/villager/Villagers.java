package io.github.dylantopic.langmod.client.villager;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.Entity;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import io.github.dylantopic.langmod.LangModConfig;
import io.github.dylantopic.langmod.core.lang.Language;
import io.github.dylantopic.langmod.core.talk.VillagerTalk;
import io.github.dylantopic.langmod.net.VillagerActionPayload;
import io.github.dylantopic.langmod.net.VillagerChoicePayload;
import io.github.dylantopic.langmod.net.VillagerReplyPayload;

/** The client's side of talking to villagers: the server's messages and what the screens share. */
public final class Villagers {
	/** As far away as the server keeps a conversation going. */
	private static final double MAX_DISTANCE_SQR = 8.0 * 8.0;

	private Villagers() {
	}

	/** The player right-clicked a villager: trade or talk? (Trades straight away if talking is turned off.) */
	public static void onChoice(VillagerChoicePayload payload) {
		Minecraft minecraft = Minecraft.getInstance();
		Language language = LangModConfig.get().languageEnum();

		if (!LangModConfig.get().talkToVillagers || !VillagerTalk.supports(language)) {
			send(new VillagerActionPayload(payload.villagerId(), VillagerActionPayload.Action.TRADE));
			return;
		}

		minecraft.setScreen(new VillagerChoiceScreen(payload.villagerId(), payload.canTrade(), language));
	}

	public static void onReply(VillagerReplyPayload payload) {
		if (Minecraft.getInstance().screen instanceof VillagerConversationScreen conversation) {
			conversation.onReply(payload);
		}
	}

	static void send(CustomPacketPayload payload) {
		if (Minecraft.getInstance().getConnection() != null && ClientPlayNetworking.canSend(payload.type())) {
			ClientPlayNetworking.send(payload);
		}
	}

	/** The villager's name as the game shows it: its name tag's, or its job's ("Farmer"). */
	static Component name(int villagerId) {
		Entity villager = entity(villagerId);
		return villager == null ? Component.translatable("entity.minecraft.villager") : villager.getDisplayName();
	}

	/** Whether the villager is still there and close enough to talk to. */
	static boolean near(int villagerId) {
		Entity villager = entity(villagerId);
		Minecraft minecraft = Minecraft.getInstance();
		return villager != null && villager.isAlive() && minecraft.player != null && minecraft.player.distanceToSqr(villager) <= MAX_DISTANCE_SQR;
	}

	private static Entity entity(int villagerId) {
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft.level == null ? null : minecraft.level.getEntity(villagerId);
	}
}
