package com.zenithclient.client.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Automatically uses experience bottles found in the hotbar or offhand.
 *
 * This follows Minecraft's normal use-item path. It does not create inventory
 * items, click hidden slots, or send custom/fabricated packets.
 */
public final class ExpThrowerController {
    private static int cooldown;
    private static boolean pitchSaved;
    private static float savedPitch;

    private ExpThrowerController() { }

    public static void tick(Minecraft mc) {
        if (!CombatUtilityState.expThrowerEnabled()
                || mc.player == null
                || mc.level == null
                || mc.gameMode == null
                || mc.screen != null) {
            onDisabled();
            return;
        }

        ThrowSource source = findSource(mc);
        if (source == null) {
            restorePitch(mc);
            cooldown = 0;
            return;
        }

        if (CombatUtilityState.expLookDown()) {
            if (!pitchSaved) {
                savedPitch = mc.player.getXRot();
                pitchSaved = true;
            }
            mc.player.setXRot(90.0F);
        } else {
            restorePitch(mc);
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
        Minecraft mc = Minecraft.getInstance();
        restorePitch(mc);
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

    private static void restorePitch(Minecraft mc) {
        if (!pitchSaved) return;
        if (mc.player != null) mc.player.setXRot(savedPitch);
        pitchSaved = false;
    }

    private record ThrowSource(InteractionHand hand, int slot) { }
}
