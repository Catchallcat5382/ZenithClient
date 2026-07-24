package com.zenithclient.client.mixin;

import com.zenithclient.client.XrayHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Optional Sodium/Iris X-Ray hook. */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer", remap = false)
public abstract class SodiumBlockRendererMixin {
    @Inject(method = "renderModel", at = @At("HEAD"), cancellable = true,
            require = 0, remap = false)
    private void zenith$hideNonTargets(@Coerce Object model, BlockState state, BlockPos pos,
                                        BlockPos origin, CallbackInfo ci) {
        if (XrayHooks.alpha(state, pos) == 0) ci.cancel();
    }
}
