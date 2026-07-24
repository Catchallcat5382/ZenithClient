package com.zenithclient.client.mixin;

import com.zenithclient.client.modules.VisualExtrasState;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class VisualExtrasTickMixin {
    @Inject(method = "tick", at = @At("TAIL"), require = 0)
    private void zenith$tickVisualExtras(CallbackInfo ci) {
        VisualExtrasState.tick((Minecraft) (Object) this);
    }
}
