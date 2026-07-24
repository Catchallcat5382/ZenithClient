package com.zenithclient.client.mixin;

import com.zenithclient.client.ZenithClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Prevents the old ZenithClient Freecam loop from fighting the detached controller. */
@Mixin(value = ZenithClient.class, remap = false)
public abstract class ZenithClientFreecamDisableMixin {
    @Inject(method = "updateFreecam", at = @At("HEAD"), cancellable = true,
            remap = false, require = 0)
    private static void zenith$disableLegacyFreecam(Minecraft client, CallbackInfo ci) {
        ci.cancel();
    }
}
