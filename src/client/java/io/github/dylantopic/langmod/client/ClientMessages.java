package io.github.dylantopic.langmod.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Chat messages shown only to the local player. */
public final class ClientMessages {
	private ClientMessages() {
	}

	public static void info(String text) {
		send(Component.literal(text).withStyle(ChatFormatting.GRAY));
	}

	public static void error(String text) {
		send(Component.literal(text).withStyle(ChatFormatting.RED));
	}

	public static void send(Component message) {
		Minecraft minecraft = Minecraft.getInstance();

		if (minecraft.player != null) {
			MutableComponent prefixed = Component.literal("[LangMod] ").withStyle(ChatFormatting.GOLD).append(message);
			minecraft.player.sendSystemMessage(prefixed);
		}
	}
}
