package com.zenithclient.client.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Automatically uses experience bottles found in the offhand or hotbar.
 *
 * The camera is never forced or locked. Bottles use the player's real current
 * view direction through Minecraft's normal use-item path.
 */
public final class ExpThrowerController {
    private static int cooldown;

    private ExpThrowerController() { }

    public static void tick(Minecraft mc) {
        if (!CombatUtilityState.expThrowerEnabled()
                || mc.player == null
                || mc.level == null
                || mc.gameMode == null
                || MinecraftScreenCompat.hasOpenScreen(mc)) {
            onDisabled();
            return;
        }

        ThrowSource source = findSource(mc);
        if (source == null) {
            cooldown = 0;
            return;
        }

        if (cooldown > 0) {
            cooldown--;
            return;
        }

        int originalSlot = mc.player.getInventory().getSelectedSlot();
        boolean swapped = source.slot >= 0 && source.slot != originalSlot;

        if (swapped) {
            mc.player.getInventory().setSelectedSlot(source.slot);
            mc.player.connection.send(new ServerboundSetCarriedItemPacket(source.slot));
        }

        mc.gameMode.useItem(mc.player, source.hand);

        if (swapped && CombatUtilityState.expSwapBack()) {
            mc.player.getInventory().setSelectedSlot(originalSlot);
            mc.player.connection.send(new ServerboundSetCarriedItemPacket(originalSlot));
        }

        cooldown = Math.max(0, CombatUtilityState.expDelayTicks() - 1);
    }

    public static void onDisabled() {
        cooldown = 0;
    }

    private static ThrowSource findSource(Minecraft mc) {
        ItemStack offhand = mc.player.getOffhandItem();
        if (offhand.is(Items.EXPERIENCE_BOTTLE)) {
            return new ThrowSource(InteractionHand.OFF_HAND, -1);
        }

        int selected = mc.player.getInventory().getSelectedSlot();
        if (mc.player.getMainHandItem().is(Items.EXPERIENCE_BOTTLE)) {
            return new ThrowSource(InteractionHand.MAIN_HAND, selected);
        }

        for (int slot = 0; slot < 9; slot++) {
            if (mc.player.getInventory().getItem(slot).is(Items.EXPERIENCE_BOTTLE)) {
                return new ThrowSource(InteractionHand.MAIN_HAND, slot);
            }
        }

        return null;
    }

    private record ThrowSource(InteractionHand hand, int slot) { }
}
