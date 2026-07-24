package com.zenithclient.client.mixin;

import com.zenithclient.client.modules.FreecamController;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class CameraFreecamMixin {
    @Shadow protected abstract void setPosition(Vec3 position);
    @Shadow protected abstract void setRotation(float yRot, float xRot);
    @Shadow private boolean detached;

    @Inject(method = "update", at = @At("TAIL"), require = 0)
    private void zenith$applyFreecam(DeltaTracker deltaTracker, CallbackInfo ci) {
        if (!FreecamController.active()) return;
        detached = true;
        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(true);
        setPosition(FreecamController.position(partialTick));
        setRotation(FreecamController.yaw(partialTick), FreecamController.pitch(partialTick));
    }

    @Inject(method = "getFluidInCamera", at = @At("HEAD"), cancellable = true, require = 0)
    private void zenith$freecamNoFluidFog(CallbackInfoReturnable<FogType> cir) {
        if (FreecamController.active()) cir.setReturnValue(FogType.NONE);
    }
}
