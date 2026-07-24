package com.zenithclient.client.mixin;

import com.zenithclient.client.XrayHooks;
import com.zenithclient.client.ZenithClient;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/** X-Ray hooks for Minecraft's normal block renderer. */
@Mixin(ModelBlockRenderer.class)
public abstract class ModelBlockRendererMixin {
    @Inject(method = {"tesselateFlat", "tesselateAmbientOcclusion"}, at = @At("HEAD"),
            cancellable = true, require = 0)
    private void zenith$hideNonTargets(BlockQuadOutput output, float x, float y, float z,
                                        List<BlockStateModelPart> parts, BlockAndTintGetter level,
                                        BlockState state, BlockPos pos, CallbackInfo ci) {
        if (XrayHooks.alpha(state, pos) == 0) ci.cancel();
    }

    @Inject(method = "shouldRenderFace", at = @At("HEAD"), cancellable = true, require = 0)
    private static void zenith$forceEveryTargetFace(BlockAndTintGetter level, BlockState state,
                                                     Direction direction, BlockPos neighborPos,
                                                     CallbackInfoReturnable<Boolean> cir) {
        if (ZenithClient.getConfig().xray && !XrayHooks.isBlocked(state.getBlock())) {
            cir.setReturnValue(true);
        }
    }
}
