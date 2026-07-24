package com.zenithclient.client.mixin;

import com.zenithclient.client.ZenithClient;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Makes an empty Entity ESP list mean no targets selected. */
@Mixin(value = ZenithClient.class, remap = false)
public abstract class EntitySelectionMixin {
    @Inject(method = "matchesEntityMode", at = @At("HEAD"), cancellable = true,
            remap = false, require = 0)
    private static void zenith$emptyEntityListMeansNone(Entity entity,
                                                         CallbackInfoReturnable<Boolean> cir) {
        String selected = ZenithClient.getConfig().entitySearch;
        if (selected == null || selected.isBlank()) cir.setReturnValue(false);
    }
}
