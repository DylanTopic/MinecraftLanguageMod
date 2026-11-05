package io.github.dylantopic.langmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

/** Calls a mob's own right-click handling, which for a villager opens the trading screen. */
@Mixin(Mob.class)
public interface MobInvoker {
	@Invoker("mobInteract")
	InteractionResult langmod$mobInteract(Player player, InteractionHand hand);
}
