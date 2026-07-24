package com.zenithclient.client.mixin;

import com.zenithclient.client.XrayHooks;
import com.zenithclient.client.ZenithClient;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Forces every selected X-Ray face through Sodium's side-culling stage. */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.model.AbstractBlockRenderContext",
        remap = false)
public abstract class SodiumBlockRenderContextMixin {
    @Shadow protected BlockState state;

    @Inject(method = "shouldDrawSide", at = @At("HEAD"), cancellable = true,
            require = 0, remap = false)
    private void zenith$forceTargetFace(Direction direction,
                                        CallbackInfoReturnable<Boolean> cir) {
        if (ZenithClient.getConfig().xray && state != null
                && !XrayHooks.isBlocked(state.getBlock())) {
            cir.setReturnValue(true);
        }
    }
}
