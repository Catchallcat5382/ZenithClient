package com.zenithclient.client.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;

/** Hotbar XP-bottle thrower with proper server-side slot synchronization and state restoration. */
public final class ExpThrower {
    private ExpThrower() {}

    public static void tick(Minecraft mc, boolean enabled) {
        if (!enabled || mc.player == null || mc.player.connection == null || mc.gameMode == null) return;

        for (int slot = 0; slot < 9; slot++) {
            if (!mc.player.getInventory().getItem(slot).is(Items.EXPERIENCE_BOTTLE)) continue;

            int previousSlot = mc.player.getInventory().getSelectedSlot();
            float previousPitch = mc.player.getXRot();
            try {
                mc.player.setXRot(90.0F);
                if (slot != previousSlot) {
                    mc.player.getInventory().setSelectedSlot(slot);
                    mc.player.connection.send(new ServerboundSetCarriedItemPacket(slot));
                }
                mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
            } finally {
                mc.player.setXRot(previousPitch);
                if (slot != previousSlot) {
                    mc.player.getInventory().setSelectedSlot(previousSlot);
                    mc.player.connection.send(new ServerboundSetCarriedItemPacket(previousSlot));
                }
            }
            return;
        }
    }
}
