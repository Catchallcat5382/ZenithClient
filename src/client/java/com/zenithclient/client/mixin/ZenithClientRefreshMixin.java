package com.zenithclient.client.mixin;

import com.zenithclient.client.ZenithClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replaces the expensive section-by-section X-Ray refresh with one renderer refresh. */
@Mixin(value = ZenithClient.class, remap = false)
public abstract class ZenithClientRefreshMixin {
    @Inject(method = "refreshWorldRenderer()V", at = @At("HEAD"), cancellable = true,
            remap = false, require = 0)
    private static void zenith$stableRendererRefresh(CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client.level != null) client.levelRenderer.allChanged();
        ci.cancel();
    }
}
