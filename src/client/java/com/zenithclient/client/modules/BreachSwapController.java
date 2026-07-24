package com.zenithclient.client.modules;

import com.zenithclient.client.ZenithClient;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * Shared fixed-height mace-smash path.
 *
 * Criticals with a mace uses the current mace. Breach Swap can first select a
 * Breach-enchanted mace from the hotbar. Both use the same fixed four-block
 * sequence, so the old configurable Mace Kill height cannot interfere.
 */
public final class BreachSwapController {
    private static final double FIXED_SMASH_HEIGHT = 4.0D;

    private static int originalSlot = -1;
    private static int restoreTicks = -1;
    private static boolean active;
    private static boolean spoofing;
    private static boolean restorePosition;
    private static double originalX;
    private static double originalY;
    private static double originalZ;

    private BreachSwapController() { }

    public static boolean beforeAttack(Entity target) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.connection == null) return false;

        boolean criticalMace = ZenithClient.getConfig().criticals
                && mc.player.getMainHandItem().getItem() instanceof MaceItem;
        boolean breachEnabled = CombatUtilityState.breachSwapEnabled();

        if (!criticalMace && !breachEnabled) return false;
        if (active) return true;

        if (breachEnabled && CombatUtilityState.breachOnlyArmored()
                && target instanceof LivingEntity living
                && living.getArmorValue() <= 0
                && !criticalMace) {
            return false;
        }

        int currentSlot = mc.player.getInventory().getSelectedSlot();
        int maceSlot = currentSlot;

        if (!(mc.player.getMainHandItem().getItem() instanceof MaceItem)) {
            if (!breachEnabled) return false;
            maceSlot = findBreachMace(mc);
            if (maceSlot < 0) return false;

            originalSlot = currentSlot;
            mc.player.getInventory().setSelectedSlot(maceSlot);
            mc.player.connection.send(new ServerboundSetCarriedItemPacket(maceSlot));
        }

        active = true;
        restoreTicks = -1;

        if (mc.player.onGround()
                && !mc.player.isInWater()
                && !mc.player.isInLava()
                && !mc.player.onClimbable()
                && !mc.player.getAbilities().flying) {
            originalX = mc.player.getX();
            originalY = mc.player.getY();
            originalZ = mc.player.getZ();
            restorePosition = true;
            spoofing = true;

            try {
                mc.player.connection.send(new ServerboundMovePlayerPacket.Pos(
                        originalX, originalY + 0.001D, originalZ, false, false));
                mc.player.connection.send(new ServerboundMovePlayerPacket.Pos(
                        originalX, originalY + FIXED_SMASH_HEIGHT, originalZ,
                        false, false));
                mc.player.connection.send(new ServerboundMovePlayerPacket.Pos(
                        originalX, originalY + 0.05D, originalZ, false, false));
                mc.player.fallDistance = (float) FIXED_SMASH_HEIGHT;
            } finally {
                spoofing = false;
            }
        }

        return true;
    }

    public static void afterAttack() {
        Minecraft mc = Minecraft.getInstance();
        if (!active || mc.player == null || mc.player.connection == null) {
            reset();
            return;
        }

        if (restorePosition) {
            spoofing = true;
            try {
                mc.player.connection.send(new ServerboundMovePlayerPacket.Pos(
                        originalX, originalY, originalZ, true, false));
            } finally {
                spoofing = false;
                restorePosition = false;
            }
        }

        if (originalSlot >= 0) {
            int delay = CombatUtilityState.breachRestoreDelay();
            if (delay <= 0) restoreSlot();
            else restoreTicks = delay;
        } else {
            active = false;
        }
    }

    public static void tick() {
        if (!active) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.connection == null) {
            reset();
            return;
        }

        if (restoreTicks >= 0 && --restoreTicks <= 0) {
            restoreSlot();
        }
    }

    public static boolean isActive() {
        return active;
    }

    public static boolean isSpoofing() {
        return spoofing;
    }

    public static void reset() {
        restorePosition = false;
        spoofing = false;
        restoreTicks = -1;

        if (originalSlot >= 0) restoreSlot();
        else active = false;
    }

    private static int findBreachMace(Minecraft mc) {
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.player.getInventory().getItem(slot);
            if (!stack.is(Items.MACE)) continue;

            for (var entry : stack.getEnchantments().entrySet()) {
                if (entry.getIntValue() > 0
                        && entry.getKey().is(Enchantments.BREACH)) {
                    return slot;
                }
            }
        }

        return -1;
    }

    private static void restoreSlot() {
        Minecraft mc = Minecraft.getInstance();
        int slot = originalSlot;

        originalSlot = -1;
        restoreTicks = -1;
        active = false;

        if (slot < 0 || mc.player == null || mc.player.connection == null) return;
        mc.player.getInventory().setSelectedSlot(slot);
        mc.player.connection.send(new ServerboundSetCarriedItemPacket(slot));
    }
}
