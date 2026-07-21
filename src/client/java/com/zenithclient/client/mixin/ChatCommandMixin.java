package com.zenithclient.client.mixin;

import com.zenithclient.client.ZenithClient;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatScreen.class)
public abstract class ChatCommandMixin {
    @Inject(method = "handleChatInput", at = @At("HEAD"), cancellable = true)
    private void zenith$handleDotCommand(String message, boolean addToHistory, CallbackInfo ci) {
        if (ZenithClient.handleChatCommand(message)) ci.cancel();
    }
}
