package com.zenithclient.client.mixin;

import com.mojang.blaze3d.vertex.QuadInstance;
import com.zenithclient.client.XrayHooks;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(ModelBlockRenderer.class)
public abstract class ModelBlockRendererMixin {
    @Shadow @Final private QuadInstance quadInstance;
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

    @Inject(method = "putQuadWithTint", at = @At("HEAD"))
    private void zenith$xrayTint(BlockQuadOutput output, float x, float y, float z, BlockAndTintGetter level,
                                 BlockState state, BlockPos pos, BakedQuad quad, CallbackInfo ci) {
        int alpha = ZENITH_ALPHA.get();
        if (alpha >= 0) quadInstance.multiplyColor(ARGB.color(alpha, 255, 255, 255));
    }

    @ModifyArg(method = "putQuadWithTint",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/block/BlockQuadOutput;put(FFFLnet/minecraft/client/resources/model/geometry/BakedQuad;Lcom/mojang/blaze3d/vertex/QuadInstance;)V"), index = 3)
    private BakedQuad zenith$xrayLayer(BakedQuad quad) {
        int alpha = ZENITH_ALPHA.get();
        if (alpha <= 0 || alpha >= 255 || quad.materialInfo().layer() == ChunkSectionLayer.TRANSLUCENT) return quad;
        BakedQuad.MaterialInfo m = quad.materialInfo();
        BakedQuad.MaterialInfo translucent = new BakedQuad.MaterialInfo(m.sprite(), ChunkSectionLayer.TRANSLUCENT,
            m.itemRenderType(), m.tintIndex(), m.shade(), m.lightEmission());
        return new BakedQuad(quad.position0(), quad.position1(), quad.position2(), quad.position3(),
            quad.packedUV0(), quad.packedUV1(), quad.packedUV2(), quad.packedUV3(), quad.direction(), translucent);
    }

    @Inject(method = "shouldRenderFace", at = @At("RETURN"), cancellable = true)
    private static void zenith$xrayFaces(BlockAndTintGetter level, BlockState state, Direction direction,
                                         BlockPos neighborPos, CallbackInfoReturnable<Boolean> cir) {
        BlockPos pos = neighborPos.relative(direction.getOpposite());
        cir.setReturnValue(XrayHooks.modifyDrawSide(state, level, pos, direction, cir.getReturnValue()));
    }
}
