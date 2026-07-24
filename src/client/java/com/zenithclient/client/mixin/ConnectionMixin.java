package com.zenithclient.client.mixin;

import com.zenithclient.client.ZenithClient;
import com.zenithclient.client.modules.FreecamController;
import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public abstract class ConnectionMixin {
    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;)V",
            at = @At("HEAD"), cancellable = true)
    private void zenith$modifyMovementPacket(Packet<?> packet,
                                              ChannelFutureListener listener,
                                              CallbackInfo ci) {
        if (FreecamController.active() && packet instanceof ServerboundMovePlayerPacket) {
            ci.cancel();
            return;
        }
        ZenithClient.modifyOutgoingMovementPacket(packet);
    }
}
