package com.zenithclient.client.mixin;

import com.zenithclient.client.ZenithClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.MaceItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps the ordinary Criticals behavior away from mace attacks.
 *
 * Mace smash depends on genuine falling movement. Sending the tiny critical
 * offsets immediately before a mace attack resets or confuses that state, so
 * mace attacks are left to the normal game rules while Criticals is enabled.
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class CriticalsMixin {
    @Inject(method = "attack", at = @At("HEAD"), require = 0)
    private void zenith$beforeAttack(Player player, Entity target, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.connection == null) return;

        var config = ZenithClient.getConfig();
        boolean holdingMace =
                mc.player.getMainHandItem().getItem() instanceof MaceItem;

        // Do not inject standard critical offsets into a mace attack. A mace
        // smash now works when the player is genuinely falling far enough.
        if (config.criticals && holdingMace) return;

        if (!mc.player.onGround()
                || mc.player.isInWater()
                || mc.player.isInLava()
                || mc.player.onClimbable()
                || mc.player.isCrouching()
                || mc.player.getAbilities().flying) {
            return;
        }

        double x = mc.player.getX();
        double y = mc.player.getY();
        double z = mc.player.getZ();

        if (config.maceKill && holdingMace) {
            double simulatedFall =
                    Math.max(4.0D, Math.min(6.0D, config.maceKillHeight));
            mc.player.connection.send(
                    new ServerboundMovePlayerPacket.Pos(
                            x, y + 0.001D, z, false, false));
            mc.player.connection.send(
                    new ServerboundMovePlayerPacket.Pos(
                            x, y + simulatedFall, z, false, false));
            mc.player.connection.send(
                    new ServerboundMovePlayerPacket.Pos(
                            x, y + 0.05D, z, false, false));
        } else if (config.criticals) {
            mc.player.connection.send(
                    new ServerboundMovePlayerPacket.Pos(
                            x, y + 0.0625D, z, false, false));
            mc.player.connection.send(
                    new ServerboundMovePlayerPacket.Pos(
                            x, y, z, false, false));
        }
    }
}
