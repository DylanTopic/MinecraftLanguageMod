package io.github.dylantopic.langmod.mixin;

import java.util.UUID;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;

import io.github.dylantopic.langmod.challenge.ChallengeManager;

/** Intercepts item pickups: a new item type goes into a pending challenge instead of the inventory. */
@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {
	@Shadow
	private int pickupDelay;

	@Shadow
	private UUID target;

	@Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
	private void langmod$interceptPickup(Player player, CallbackInfo ci) {
		// Same preconditions as vanilla, so we only step in when the item would really be picked up.
		if (player instanceof ServerPlayer serverPlayer
				&& this.pickupDelay == 0
				&& (this.target == null || this.target.equals(player.getUUID()))
				&& ChallengeManager.get().onPlayerTouchItem(serverPlayer, (ItemEntity) (Object) this)) {
			ci.cancel();
		}
	}
}
