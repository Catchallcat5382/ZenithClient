package com.zenithclient.client.mixin;

import com.zenithclient.client.ZenithClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.MaceItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Sends the movement sequence immediately before vanilla sends the attack packet. */
@Mixin(MultiPlayerGameMode.class)
public abstract class CriticalsMixin {
    @Unique
    private static boolean zenith$spoofingMaceSmash;

    @Inject(method = "attack", at = @At("HEAD"), require = 0)
    private void zenith$beforeAttack(Player player, Entity target, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.connection == null) return;
        var config = ZenithClient.getConfig();

        if (config.criticals && mc.player.onGround() && !mc.player.isInWater()
                && !mc.player.isInLava() && !mc.player.onClimbable()) {
            double x = mc.player.getX();
            double y = mc.player.getY();
            double z = mc.player.getZ();
            boolean mace = mc.player.getMainHandItem().getItem() instanceof MaceItem;

            if (mace) {
                // More than the 1.5-block smash threshold, followed by a descending airborne position.
                // The guard is exposed so a NoFall packet mixin can leave this sequence untouched.
                zenith$spoofingMaceSmash = true;
                try {
                    mc.player.connection.send(new ServerboundMovePlayerPacket.Pos(x, y + 0.001D, z, false, false));
                    mc.player.connection.send(new ServerboundMovePlayerPacket.Pos(x, y + 2.10D, z, false, false));
                    mc.player.connection.send(new ServerboundMovePlayerPacket.Pos(x, y + 0.05D, z, false, false));
                } finally {
                    zenith$spoofingMaceSmash = false;
                }
            } else {
                mc.player.connection.send(new ServerboundMovePlayerPacket.Pos(x, y + 0.0625D, z, false, false));
                mc.player.connection.send(new ServerboundMovePlayerPacket.Pos(x, y, z, false, false));
            }
        }
        ZenithClient.beforeAttack(target);
    }

    @Inject(method = "attack", at = @At("TAIL"), require = 0)
    private void zenith$afterAttack(Player player, Entity target, CallbackInfo ci) {
        ZenithClient.afterAttack(target);
    }
}
