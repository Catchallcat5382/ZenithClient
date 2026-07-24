package com.zenithclient.client.mixin;

import com.zenithclient.client.ZenithClient;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Performs the carried-item change at the start of Minecraft's attack flow. */
@Mixin(Minecraft.class)
public abstract class AttributeSwapMixin {
    @Unique private static int zenith$previousSlot = -1;
    @Unique private static int zenith$restoreTicks = -1;

    @Inject(method = "startAttack", at = @At("HEAD"), require = 0)
    private void zenith$swapBeforeAttack(CallbackInfoReturnable<Boolean> cir) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.connection == null) return;
        var config = ZenithClient.getConfig();
        if (!config.attributeSwap || zenith$previousSlot >= 0) return;

        int target = Math.max(0, Math.min(8, config.attributeSwapSlot - 1));
        int current = mc.player.getInventory().getSelectedSlot();
        if (target == current) return;

        zenith$previousSlot = current;
        zenith$restoreTicks = Math.max(4, Math.min(20, config.attributeSwapRestoreDelayTicks));
        // Update both local selection and the server-carried slot before vanilla creates the attack.
        // A minimum four-tick hold prevents the restore packet from overtaking or sharing the attack flush.
        mc.player.getInventory().setSelectedSlot(target);
        mc.player.connection.send(new ServerboundSetCarriedItemPacket(target));
    }

    @Inject(method = "tick", at = @At("TAIL"), require = 0)
    private void zenith$restoreAfterAttack(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (zenith$previousSlot < 0 || zenith$restoreTicks-- > 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.connection != null) {
            int slot = zenith$previousSlot;
            mc.player.getInventory().setSelectedSlot(slot);
            mc.player.connection.send(new ServerboundSetCarriedItemPacket(slot));
        }
        zenith$previousSlot = -1;
        zenith$restoreTicks = -1;
    }
}
