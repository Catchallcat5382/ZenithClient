package com.zenithclient.client.mixin;

import com.zenithclient.client.XrayHooks;
import com.zenithclient.client.ZenithClient;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Gives selected X-Ray blocks their own maximum client-side lighting. */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class XrayLightingMixin {
    @Inject(method = "getLightEmission", at = @At("HEAD"), cancellable = true, require = 0)
    private void zenith$xrayEmission(CallbackInfoReturnable<Integer> cir) {
        BlockState state = (BlockState) (Object) this;
        if (ZenithClient.getConfig().xray && !XrayHooks.isBlocked(state.getBlock())) {
            cir.setReturnValue(15);
        }
    }

    @Inject(method = "getShadeBrightness", at = @At("HEAD"), cancellable = true, require = 0)
    private void zenith$xrayShade(BlockGetter level, BlockPos pos,
                                  CallbackInfoReturnable<Float> cir) {
        BlockState state = (BlockState) (Object) this;
        if (ZenithClient.getConfig().xray && !XrayHooks.isBlocked(state.getBlock())) {
            cir.setReturnValue(1.0F);
        }
    }
}
