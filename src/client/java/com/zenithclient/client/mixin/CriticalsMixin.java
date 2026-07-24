package com.zenithclient.client.mixin;

import com.zenithclient.client.ZenithClient;
import com.zenithclient.client.modules.BreachSwapController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MultiPlayerGameMode.class, priority = 1200)
public abstract class CriticalsMixin {
    @Inject(method = "attack", at = @At("HEAD"), require = 0)
    private void zenith$beforeAttack(Player player, Entity target, CallbackInfo ci) {
        if (BreachSwapController.beforeAttack(target)) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.connection == null) return;

        var config = ZenithClient.getConfig();
        if (!config.criticals
                || !mc.player.onGround()
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

        mc.player.connection.send(new ServerboundMovePlayerPacket.Pos(
                x, y + 0.0625D, z, false, false));
        mc.player.connection.send(new ServerboundMovePlayerPacket.Pos(
                x, y, z, false, false));
    }

    @Inject(method = "attack", at = @At("TAIL"), require = 0)
    private void zenith$afterAttack(Player player, Entity target, CallbackInfo ci) {
        BreachSwapController.afterAttack();
    }
}
