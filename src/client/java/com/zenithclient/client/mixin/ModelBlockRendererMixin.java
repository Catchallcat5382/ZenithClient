package com.zenithclient.client.mixin;

import com.zenithclient.client.XrayHooks;
import com.zenithclient.client.ZenithClient;
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
    @Inject(method = "getRenderShape", at = @At("HEAD"), cancellable = true, require = 0)
    private void zenith$xrayRenderShape(CallbackInfoReturnable<RenderShape> cir) {
        BlockState state = (BlockState) (Object) this;
        if (XrayHooks.alpha(state, null) == 0) cir.setReturnValue(RenderShape.INVISIBLE);
    }

    @Inject(method = "skipRendering", at = @At("HEAD"), cancellable = true, require = 0)
    private void zenith$xrayFaces(BlockState adjacent, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (!ZenithClient.getConfig().xray) return;
        BlockState state = (BlockState) (Object) this;
        if (!XrayHooks.isBlocked(state.getBlock()) && XrayHooks.isBlocked(adjacent.getBlock())) {
            cir.setReturnValue(false);
        }
    }
}
