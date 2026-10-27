package io.github.dylantopic.langmod.command;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import io.github.dylantopic.langmod.net.ClientActionPayload;

/**
 * {@code /langmod ...}. All subcommands are server commands (Fabric cannot mix client and server
 * subcommands under one root); the ones that need the microphone ask the client to do the work.
 */
public final class LangModCommands {
	private LangModCommands() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
				Commands.literal("langmod")
						.then(Commands.literal("test").executes(context -> clientAction(context, ClientActionPayload.Action.MIC_TEST)))
						.then(Commands.literal("mics").executes(context -> clientAction(context, ClientActionPayload.Action.LIST_MICS)))
		));
	}

	private static int clientAction(CommandContext<CommandSourceStack> context, ClientActionPayload.Action action) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();

		if (!ServerPlayNetworking.canSend(player, ClientActionPayload.TYPE)) {
			context.getSource().sendFailure(Component.literal("LangMod is not installed on your client."));
			return 0;
		}

		ServerPlayNetworking.send(player, new ClientActionPayload(action));
		return 1;
	}
}
