package com.zenithclient.client.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * Selects a Breach-enchanted mace immediately before the normal attack call.
 *
 * The swap is local plus the standard selected-hotbar-slot packet. No movement
 * packets are generated, so it can coexist with Criticals and No Fall without
 * either module overwriting the selected weapon.
 */
public final class BreachSwapController {
    private static int originalSlot = -1;
    private static int restoreTicks = -1;

    private BreachSwapController() { }

    public static void beforeAttack(Entity target) {
        Minecraft mc = Minecraft.getInstance();

        if (!CombatUtilityState.breachSwapEnabled()
                || mc.player == null
                || mc.player.connection == null
                || originalSlot >= 0
                || !(target instanceof LivingEntity living)) {
            return;
        }

        if (CombatUtilityState.breachOnlyArmored() && living.getArmorValue() <= 0) {
            return;
        }

        int breachSlot = findBreachMace();
        if (breachSlot < 0) return;

        int current = mc.player.getInventory().getSelectedSlot();
        if (current == breachSlot) return;

        originalSlot = current;
        restoreTicks = -1;

        mc.player.getInventory().setSelectedSlot(breachSlot);
        mc.player.connection.send(new ServerboundSetCarriedItemPacket(breachSlot));
    }

    public static void afterAttack() {
        if (originalSlot < 0) return;

        int delay = CombatUtilityState.breachRestoreDelay();
        if (delay <= 0) {
            restore();
        } else {
            restoreTicks = delay;
        }
    }

    public static void tick() {
        if (originalSlot < 0 || restoreTicks < 0) return;
        if (--restoreTicks > 0) return;
        restore();
    }

    public static boolean isSwapActive() {
        return originalSlot >= 0;
    }

    public static void reset() {
        if (originalSlot >= 0) restore();
        restoreTicks = -1;
    }

    private static int findBreachMace() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return -1;

        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.player.getInventory().getItem(slot);
            if (!stack.is(Items.MACE)) continue;
            if (hasBreach(stack)) return slot;
        }

        return -1;
    }

    private static boolean hasBreach(ItemStack stack) {
        for (var entry : stack.getEnchantments().entrySet()) {
            if (entry.getIntValue() > 0 && entry.getKey().is(Enchantments.BREACH)) {
                return true;
            }
        }
        return false;
    }

    private static void restore() {
        Minecraft mc = Minecraft.getInstance();
        int slot = originalSlot;

        originalSlot = -1;
        restoreTicks = -1;

        if (slot < 0 || mc.player == null || mc.player.connection == null) return;
        mc.player.getInventory().setSelectedSlot(slot);
        mc.player.connection.send(new ServerboundSetCarriedItemPacket(slot));
    }
}
