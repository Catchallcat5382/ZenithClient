package com.zenithclient.client.mixin;

import com.zenithclient.client.modules.FreecamController;
import net.minecraft.client.renderer.chunk.VisGraph;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Makes loaded chunks behave like spectator rendering while Freecam is active.
 *
 * VisGraph normally marks solid blocks as occluders. Cancelling that marking
 * keeps geometry behind the player's original viewing direction available to
 * the detached camera. This does not create or download chunks the server has
 * not sent to the client.
 */
@Mixin(VisGraph.class)
public abstract class FreecamOcclusionMixin {
    @Inject(method = "setOpaque", at = @At("HEAD"), cancellable = true, require = 0)
    private void zenith$disableFreecamOcclusion(BlockPos pos, CallbackInfo ci) {
        if (FreecamController.active()) ci.cancel();
    }
}
