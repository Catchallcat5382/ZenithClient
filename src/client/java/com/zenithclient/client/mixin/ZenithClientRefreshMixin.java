package com.zenithclient.client.mixin;

import com.zenithclient.client.ZenithClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;

/** Uses the fast renderer refresh when the active Minecraft version exposes it. */
@Mixin(value = ZenithClient.class, remap = false)
public abstract class ZenithClientRefreshMixin {
    @Inject(method = "refreshWorldRenderer()V", at = @At("HEAD"), cancellable = true,
            remap = false, require = 0)
    private static void zenith$stableRendererRefresh(CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.levelRenderer == null) return;

        // Minecraft 26.2 no longer exposes LevelRenderer#allChanged at compile time.
        // Use it reflectively when available; otherwise allow ZenithClient's
        // version-compatible section refresh implementation to run normally.
        try {
            Method method = client.levelRenderer.getClass().getMethod("allChanged");
            method.invoke(client.levelRenderer);
            ci.cancel();
        } catch (ReflectiveOperationException ignored) {
            // Fall through to ZenithClient.refreshWorldRenderer().
        }
    }
}
