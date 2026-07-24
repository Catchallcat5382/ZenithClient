package com.zenithclient.client.mixin;

import com.zenithclient.client.modules.BreachSwapController;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Higher priority than the generic Attribute Swap so a real Breach candidate
 * gets first choice. The generic swap manager skips only while this swap is
 * actually active.
 */
@Mixin(value = MultiPlayerGameMode.class, priority = 1200)
public abstract class BreachSwapMixin {
    @Inject(method = "attack", at = @At("HEAD"), require = 0)
    private void zenith$breachBeforeAttack(Player player, Entity target, CallbackInfo ci) {
        BreachSwapController.beforeAttack(target);
    }

    @Inject(method = "attack", at = @At("TAIL"), require = 0)
    private void zenith$breachAfterAttack(Player player, Entity target, CallbackInfo ci) {
        BreachSwapController.afterAttack();
    }
}
