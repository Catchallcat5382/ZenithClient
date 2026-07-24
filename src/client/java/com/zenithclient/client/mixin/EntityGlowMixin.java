package com.zenithclient.client.mixin;

import com.zenithclient.client.ZenithClient;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Adds Zenith ESP glow without disabling normal vanilla glowing. */
@Mixin(Entity.class)
public abstract class EntityGlowMixin {
    @Inject(method = "isCurrentlyGlowing", at = @At("RETURN"), cancellable = true, require = 0)
    private void zenith$espGlow(CallbackInfoReturnable<Boolean> cir) {
        Minecraft mc = Minecraft.getInstance();
        Entity self = (Entity) (Object) this;
        if (mc.player == null || mc.level == null || self == mc.player) return;
        if (ZenithClient.shouldGlowEsp(self) || ZenithClient.isTrajectoryTarget(self)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "getTeamColor", at = @At("RETURN"), cancellable = true, require = 0)
    private void zenith$espColor(CallbackInfoReturnable<Integer> cir) {
        Entity self = (Entity) (Object) this;
        int color = ZenithClient.espColor(self);
        if (color >= 0) cir.setReturnValue(color & 0xFFFFFF);
    }
}
