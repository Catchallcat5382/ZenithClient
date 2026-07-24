package com.zenithclient.client.mixin;

import com.zenithclient.client.ZenithClient;
import com.zenithclient.client.modules.BreachSwapController;
import com.zenithclient.client.modules.FreecamController;
import io.netty.channel.ChannelFutureListener;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.item.MaceItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public abstract class ConnectionMixin {
    @Inject(
            method = "send(Lnet/minecraft/network/protocol/Packet;"
                    + "Lio/netty/channel/ChannelFutureListener;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void zenith$modifyMovementPacket(
            Packet<?> packet,
            ChannelFutureListener listener,
            CallbackInfo ci
    ) {
        if (FreecamController.active()
                && packet instanceof ServerboundMovePlayerPacket) {
            ci.cancel();
            return;
        }

        if (BreachSwapController.isSpoofing()
                && packet instanceof ServerboundMovePlayerPacket) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        var config = ZenithClient.getConfig();

        if (packet instanceof ServerboundMovePlayerPacket
                && config.noFall
                && config.criticals
                && mc.player != null
                && mc.player.getMainHandItem().getItem() instanceof MaceItem
                && !mc.player.onGround()
                && mc.player.getDeltaMovement().y < 0.0D) {
            return;
        }

        ZenithClient.modifyOutgoingMovementPacket(packet);
    }
}
