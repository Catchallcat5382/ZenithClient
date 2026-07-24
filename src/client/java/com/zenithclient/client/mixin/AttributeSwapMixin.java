package com.zenithclient.client.mixin;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Swaps immediately around the actual vanilla attack method. */
@Mixin(MultiPlayerGameMode.class)
public abstract class AttributeSwapMixin {
    @Inject(method = "attack", at = @At("HEAD"), require = 0)
    private void zenith$swapBeforeAttack(Player player, Entity target, CallbackInfo ci) {
        AttributeSwapManager.beforeAttack(player);
    }

    @Inject(method = "attack", at = @At("TAIL"), require = 0)
    private void zenith$scheduleRestore(Player player, Entity target, CallbackInfo ci) {
        AttributeSwapManager.afterAttack();
    }
}
