package com.zenithclient.client.mixin;

import com.zenithclient.client.XrayHooks;
import com.zenithclient.client.ZenithClient;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;

/** Optional Sodium/Iris X-Ray mesh and lighting hook. */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer",
        remap = false)
public abstract class SodiumBlockRendererMixin {
    @Unique private boolean zenith$brightTarget;

    @Inject(method = "renderModel", at = @At("HEAD"), cancellable = true,
            require = 0, remap = false)
    private void zenith$prepareXray(@Coerce Object model, BlockState state, BlockPos pos,
                                    BlockPos origin, CallbackInfo ci) {
        int alpha = XrayHooks.alpha(state, pos);
        zenith$brightTarget = ZenithClient.getConfig().xray && alpha != 0
                && !XrayHooks.isBlocked(state.getBlock());
        if (alpha == 0) ci.cancel();
    }

    @Inject(method = "bufferQuad", at = @At("HEAD"), require = 0, remap = false)
    private void zenith$brightenTarget(@Coerce Object quad, float[] brightnesses,
                                      @Coerce Object material, CallbackInfo ci) {
        if (zenith$brightTarget && brightnesses != null) {
            Arrays.fill(brightnesses, 1.0F);
        }
    }
}
