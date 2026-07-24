package com.zenithclient.client.mixin;

import com.zenithclient.client.modules.EspGlowTracker;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Stable ESP glow override with cleanup for previously controlled entities. */
@Mixin(Entity.class)
public abstract class EntityGlowMixin {
    @Inject(method = "isCurrentlyGlowing", at = @At("RETURN"), cancellable = true, require = 0)
    private void zenith$espGlow(CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity) (Object) this;
        cir.setReturnValue(EspGlowTracker.resolveGlow(self, cir.getReturnValue()));
    }

    @Inject(method = "getTeamColor", at = @At("RETURN"), cancellable = true, require = 0)
    private void zenith$espColor(CallbackInfoReturnable<Integer> cir) {
        int color = EspGlowTracker.color((Entity) (Object) this);
        if (color >= 0) cir.setReturnValue(color);
    }
}
