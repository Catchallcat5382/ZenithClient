package com.zenithclient.client.mixin;

import com.zenithclient.client.ZenithClient;
import com.zenithclient.client.modules.FreecamController;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class FreecamTickMixin {
    @Inject(method = "tick", at = @At("TAIL"), require = 0)
    private void zenith$tickFreecam(CallbackInfo ci) {
        Minecraft mc = (Minecraft) (Object) this;
        var config = ZenithClient.getConfig();
        FreecamController.tick(mc, config.freecam, config.freecamSpeed);
    }
}
