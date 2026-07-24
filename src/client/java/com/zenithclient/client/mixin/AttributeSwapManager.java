package com.zenithclient.client.mixin;

import com.zenithclient.client.ZenithClient;
import com.zenithclient.client.ZenithConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.entity.player.Player;

/** Single source of truth for Attribute Swap state. */
final class AttributeSwapManager {
    private static int originalSlot = -1;
    private static int restoreTicks = -1;

    private AttributeSwapManager() { }

    static void beforeAttack(Player player) {
        ZenithConfig config = ZenithClient.getConfig();
        if (!config.attributeSwap || player == null || player.connection == null || originalSlot >= 0) return;

        int current = player.getInventory().getSelectedSlot();
        int wanted = Math.max(0, Math.min(8, config.attributeSwapSlot - 1));
        if (current == wanted) return;

        originalSlot = current;
        restoreTicks = -1;
        player.getInventory().setSelectedSlot(wanted);
        player.connection.send(new ServerboundSetCarriedItemPacket(wanted));
    }

    static void afterAttack() {
        if (originalSlot < 0) return;
        restoreTicks = Math.max(1, Math.min(20,
                ZenithClient.getConfig().attributeSwapRestoreDelayTicks));
    }

    static void tick() {
        if (originalSlot < 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.connection == null) {
            originalSlot = -1;
            restoreTicks = -1;
            return;
        }
        if (restoreTicks < 0 || --restoreTicks > 0) return;

        int slot = originalSlot;
        originalSlot = -1;
        restoreTicks = -1;
        mc.player.getInventory().setSelectedSlot(slot);
        mc.player.connection.send(new ServerboundSetCarriedItemPacket(slot));
    }
}
