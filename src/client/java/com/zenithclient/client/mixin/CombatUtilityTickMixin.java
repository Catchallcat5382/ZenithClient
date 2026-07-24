package com.zenithclient.client.mixin;

import com.zenithclient.client.modules.CombatUtilityState;
import com.zenithclient.client.modules.ExpThrowerController;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class CombatUtilityTickMixin {
    @Inject(method = "tick", at = @At("TAIL"), require = 0)
    private void zenith$tickCombatUtilities(CallbackInfo ci) {
        Minecraft mc = (Minecraft) (Object) this;
        CombatUtilityState.handleKeybinds(mc);
        ExpThrowerController.tick(mc);
    }
}
