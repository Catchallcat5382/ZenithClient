package com.zenithclient.client.mixin;

import com.zenithclient.client.ZenithClient;
import com.zenithclient.client.ZenithConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;

final class AttributeSwapManager {
    private static int originalSlot = -1;
    private static int restoreTicks = -1;

    private AttributeSwapManager() { }

    static void beforeAttack() {
        Minecraft client = Minecraft.getInstance();
        ZenithConfig config = ZenithClient.getConfig();
        if (!config.attributeSwap || client.player == null || client.player.connection == null) return;

        int current = selectedSlot(client);
        int wanted = Math.max(0, Math.min(8, config.attributeSwapSlot - 1));
        if (current == wanted) return;

        originalSlot = current;
        restoreTicks = -1;
        setSelectedSlot(client, wanted);
        client.player.connection.send(new ServerboundSetCarriedItemPacket(wanted));
    }

    static void afterAttack() {
        if (originalSlot < 0) return;
        ZenithConfig config = ZenithClient.getConfig();
        restoreTicks = Math.max(1, Math.min(5, config.attributeSwapRestoreDelayTicks));
    }

    static void tick() {
        if (restoreTicks < 0) return;
        if (--restoreTicks > 0) return;

        Minecraft client = Minecraft.getInstance();
        int slot = originalSlot;
        originalSlot = -1;
        restoreTicks = -1;

        if (slot < 0 || client.player == null || client.player.connection == null) return;
        setSelectedSlot(client, slot);
        client.player.connection.send(new ServerboundSetCarriedItemPacket(slot));
    }

    private static int selectedSlot(Minecraft client) {
        Object inventory = client.player.getInventory();
        try {
            return (int) inventory.getClass().getMethod("getSelectedSlot").invoke(inventory);
        } catch (ReflectiveOperationException ignored) {
            for (String name : new String[]{"selected", "selectedSlot"}) {
                try {
                    var field = inventory.getClass().getDeclaredField(name);
                    field.setAccessible(true);
                    return field.getInt(inventory);
                } catch (ReflectiveOperationException ignoredAgain) { }
            }
            return 0;
        }
    }

    private static void setSelectedSlot(Minecraft client, int slot) {
        Object inventory = client.player.getInventory();
        try {
            inventory.getClass().getMethod("setSelectedSlot", int.class).invoke(inventory, slot);
            return;
        } catch (ReflectiveOperationException ignored) { }

        for (String name : new String[]{"selected", "selectedSlot"}) {
            try {
                var field = inventory.getClass().getDeclaredField(name);
                field.setAccessible(true);
                field.setInt(inventory, slot);
                return;
            } catch (ReflectiveOperationException ignoredAgain) { }
        }
    }
}
