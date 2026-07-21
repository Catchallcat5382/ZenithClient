package com.zenithclient.client.mixin;

import com.zenithclient.client.XrayHooks;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(ModelBlockRenderer.class)
public abstract class ModelBlockRendererMixin {
    @Unique private static final ThreadLocal<Integer> ZENITH_ALPHA = ThreadLocal.withInitial(() -> -1);

    @Inject(method = {"tesselateFlat", "tesselateAmbientOcclusion"}, at = @At("HEAD"), cancellable = true)
    private void zenith$xrayStart(BlockQuadOutput output, float x, float y, float z, List<BlockStateModelPart> parts,
                                  BlockAndTintGetter level, BlockState state, BlockPos pos, CallbackInfo ci) {
        int alpha = XrayHooks.alpha(state, pos);
        ZENITH_ALPHA.set(alpha);
        if (alpha == 0) ci.cancel();
    }

    @Inject(method = {"tesselateFlat", "tesselateAmbientOcclusion"}, at = @At("RETURN"))
    private void zenith$xrayEnd(BlockQuadOutput output, float x, float y, float z, List<BlockStateModelPart> parts,
                                BlockAndTintGetter level, BlockState state, BlockPos pos, CallbackInfo ci) {
        ZENITH_ALPHA.set(-1);
    }

    @Inject(method = "shouldRenderFace", at = @At("RETURN"), cancellable = true)
    private static void zenith$xrayFaces(BlockAndTintGetter level, BlockState state, Direction direction,
                                         BlockPos neighborPos, CallbackInfoReturnable<Boolean> cir) {
        BlockPos pos = neighborPos.relative(direction.getOpposite());
        cir.setReturnValue(XrayHooks.modifyDrawSide(state, level, pos, direction, cir.getReturnValue()));
    }
}
