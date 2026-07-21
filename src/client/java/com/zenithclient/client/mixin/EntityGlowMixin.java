package com.zenithclient.client.mixin;

import com.zenithclient.client.ZenithClient;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityGlowMixin {
    @Inject(method = "isCurrentlyGlowing", at = @At("HEAD"), cancellable = true)
    private void zenith$forceEspGlow(CallbackInfoReturnable<Boolean> cir) {
        Minecraft client = Minecraft.getInstance();
        Entity self = (Entity) (Object) this;
        if (client.player == null || client.level == null || self == client.player) return;
        if (ZenithClient.shouldGlowEsp(self) || ZenithClient.isTrajectoryTarget(self)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true)
    private void zenith$espGlowColor(CallbackInfoReturnable<Integer> cir) {
        Entity self = (Entity) (Object) this;
        int color = ZenithClient.espColor(self);
        if (color >= 0) cir.setReturnValue(color & 0xFFFFFF);
    }
}
