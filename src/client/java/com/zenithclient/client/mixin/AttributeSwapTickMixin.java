package com.zenithclient.client.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class AttributeSwapTickMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void zenith$tickAttributeSwap(CallbackInfo ci) {
        AttributeSwapManager.tick();
    }
}
