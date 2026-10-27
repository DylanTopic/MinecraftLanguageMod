package io.github.dylantopic.langmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;

import io.github.dylantopic.langmod.challenge.ChallengeManager;

/**
 * Gives held challenge items back at the start of death, before the inventory is dropped, so they
 * behave like everything else the player carries (dropped, or kept with keepInventory).
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {
	@Inject(method = "die", at = @At("HEAD"))
	private void langmod$returnHeldItems(DamageSource source, CallbackInfo ci) {
		ChallengeManager.get().onDeath((ServerPlayer) (Object) this);
	}
}
