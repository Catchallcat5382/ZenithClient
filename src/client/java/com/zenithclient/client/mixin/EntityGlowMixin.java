package com.zenithclient.client.mixin;

import com.zenithclient.client.ZenithClient;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityGlowMixin {
    @Inject(method = "isCurrentlyGlowing", at = @At("HEAD"), cancellable = true)
    private void zenith$forceEspGlow(CallbackInfoReturnable<Boolean> cir) {
        Minecraft client = Minecraft.getInstance();
        Entity self = (Entity) (Object) this;
        if (client.player == null || client.level == null || self == client.player) return;
        var config = ZenithClient.getConfig();
        boolean inRange = self.distanceToSqr(client.player) <= (double) config.entityRange * config.entityRange;
        boolean selectedPlayer = self instanceof net.minecraft.world.entity.player.Player && config.playerEsp;
        boolean selectedEntity = !(self instanceof net.minecraft.world.entity.player.Player)
                && config.entityHighlights && ZenithClient.matchesEntityMode(self);
        if ((inRange && (selectedPlayer || selectedEntity)) || ZenithClient.isTrajectoryTarget(self)) {
            cir.setReturnValue(true);
        }
    }
}
