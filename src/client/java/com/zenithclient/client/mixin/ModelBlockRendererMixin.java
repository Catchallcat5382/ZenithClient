package com.zenithclient.client.mixin;

import com.zenithclient.client.XrayHooks;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class ModelBlockRendererMixin {
    @Inject(method = "getRenderShape", at = @At("HEAD"), cancellable = true)
    private void zenith$xrayRenderShape(CallbackInfoReturnable<RenderShape> cir) {
        BlockState state = (BlockState) (Object) this;
        int alpha = XrayHooks.alpha(state, null);
        if (alpha == 0) cir.setReturnValue(RenderShape.INVISIBLE);
    }

    @Inject(method = "skipRendering", at = @At("HEAD"), cancellable = true)
    private void zenith$xrayFaces(BlockState adjacent, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        BlockState state = (BlockState) (Object) this;
        // Whitelisted ore beside an invisible block must render that face.
        // The previous alpha == 255 check could never be true because visible
        // blocks use -1 (vanilla rendering), which made X-ray appear empty.
        if (!XrayHooks.isBlocked(state.getBlock()) && XrayHooks.isBlocked(adjacent.getBlock())) {
            cir.setReturnValue(false);
        }
    }
}
