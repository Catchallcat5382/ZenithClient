package com.zenithclient.client.modules;

import com.zenithclient.client.ZenithClient;
import com.zenithclient.client.ZenithConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;

/**
 * Runtime state for Attribute Swap.
 *
 * This class deliberately lives outside the Mixin package. Sponge Mixin owns
 * every class in the configured mixin package and rejects normal helper
 * classes when Minecraft attempts to load them directly.
 */
public final class AttributeSwapManager {
    private static int originalSlot = -1;
    private static int restoreTicks = -1;

    private AttributeSwapManager() { }

    public static void beforeAttack() {
        Minecraft mc = Minecraft.getInstance();
        ZenithConfig config = ZenithClient.getConfig();

        if (!config.attributeSwap || mc.player == null || mc.player.connection == null
                || originalSlot >= 0 || BreachSwapController.isActive()) {
            return;
        }

        int current = mc.player.getInventory().getSelectedSlot();
        int wanted = Math.max(0, Math.min(8, config.attributeSwapSlot - 1));
        if (current == wanted) return;

        originalSlot = current;
        restoreTicks = -1;

        // Keep the local hotbar and the server-held slot synchronized before
        // vanilla sends the attack packet.
        mc.player.getInventory().setSelectedSlot(wanted);
        mc.player.connection.send(new ServerboundSetCarriedItemPacket(wanted));
    }

    public static void afterAttack() {
        if (originalSlot < 0) return;

        restoreTicks = Math.max(
                1,
                Math.min(20, ZenithClient.getConfig().attributeSwapRestoreDelayTicks)
        );
    }

    public static void tick() {
        if (originalSlot < 0) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.connection == null) {
            clear();
            return;
        }

        // Wait until the attack hook schedules a restore.
        if (restoreTicks < 0) return;
        if (--restoreTicks > 0) return;

        int slot = originalSlot;
        clear();

        mc.player.getInventory().setSelectedSlot(slot);
        mc.player.connection.send(new ServerboundSetCarriedItemPacket(slot));
    }

    private static void clear() {
        originalSlot = -1;
        restoreTicks = -1;
    }
}
