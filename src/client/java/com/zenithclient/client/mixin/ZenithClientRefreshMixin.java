package com.zenithclient.client.mixin;

import com.zenithclient.client.ZenithClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replaces the old nested section-dirty loop with one renderer-wide refresh. */
@Mixin(value = ZenithClient.class, remap = false)
public abstract class ZenithClientRefreshMixin {
    @Inject(method = "refreshWorldRenderer", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void zenith$stableRendererRefresh(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) mc.levelRenderer.allChanged();
        ci.cancel();
    }
}
