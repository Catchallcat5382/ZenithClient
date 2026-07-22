package com.zenithclient.client.mixin;

import com.zenithclient.client.ZenithClient;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class NoFireOverlayMixin {
    @Inject(method = "isOnFire", at = @At("HEAD"), cancellable = true)
    private void zenith$hideLocalFireOverlay(CallbackInfoReturnable<Boolean> cir) {
        Minecraft client = Minecraft.getInstance();
        if (ZenithClient.getConfig().noFireOverlay && client.player != null && (Object) this == client.player) {
            cir.setReturnValue(false);
        }
    }
}
