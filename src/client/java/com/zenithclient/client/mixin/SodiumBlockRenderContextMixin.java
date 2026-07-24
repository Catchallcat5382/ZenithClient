package com.zenithclient.client.mixin;

import com.zenithclient.client.XrayHooks;
import com.zenithclient.client.ZenithClient;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Forces every face of selected X-Ray blocks through Sodium's culling stage. */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.model.AbstractBlockRenderContext", remap = false)
public abstract class SodiumBlockRenderContextMixin {
    @Unique private static java.lang.reflect.Field zenith$stateField;

    @Inject(method = "shouldDrawSide", at = @At("HEAD"), cancellable = true,
            require = 0, remap = false)
    private void zenith$forceTargetFace(Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (!ZenithClient.getConfig().xray) return;
        BlockState state = zenith$state();
        if (state != null && !XrayHooks.isBlocked(state.getBlock())) cir.setReturnValue(true);
    }

    @Unique
    private BlockState zenith$state() {
        try {
            if (zenith$stateField == null) {
                Class<?> type = this.getClass();
                while (type != null && zenith$stateField == null) {
                    try {
                        zenith$stateField = type.getDeclaredField("state");
                        zenith$stateField.setAccessible(true);
                    } catch (NoSuchFieldException ignored) {
                        type = type.getSuperclass();
                    }
                }
            }
            if (zenith$stateField == null) return null;
            Object value = zenith$stateField.get(this);
            return value instanceof BlockState blockState ? blockState : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
