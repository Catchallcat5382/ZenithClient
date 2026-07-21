package com.zenithclient.client.mixin;

import com.zenithclient.client.ZenithClient;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;

@Mixin(ChatScreen.class)
public abstract class ChatCommandMixin {
    @Inject(method = "handleChatInput", at = @At("HEAD"), cancellable = true)
    private void zenith$handleDotCommand(String message, boolean addToHistory, CallbackInfo ci) {
        if (ZenithClient.handleChatCommand(message)) ci.cancel();
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void zenith$completeDotCommand(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (event.key() != GLFW.GLFW_KEY_TAB) return;
        Object input = findInputBox();
        if (input == null) return;
        String value = invokeString(input, "getValue");
        if (value == null || !value.startsWith(".") || !".autovaultclip".startsWith(value.toLowerCase(java.util.Locale.ROOT))) return;
        invokeVoidString(input, "setValue", ".autovaultclip ");
        cir.setReturnValue(true);
    }

    private Object findInputBox() {
        for (Field field : ChatScreen.class.getDeclaredFields()) {
            if (!field.getType().getName().contains("EditBox")) continue;
            try {
                field.setAccessible(true);
                Object value = field.get(this);
                if (value != null) return value;
            } catch (IllegalAccessException ignored) {
                return null;
            }
        }
        return null;
    }

    private static String invokeString(Object target, String method) {
        try {
            Object value = target.getClass().getMethod(method).invoke(target);
            return value instanceof String string ? string : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static void invokeVoidString(Object target, String method, String value) {
        try {
            target.getClass().getMethod(method, String.class).invoke(target, value);
        } catch (ReflectiveOperationException ignored) {
            // Chat internals changed; command execution still works without autocomplete.
        }
    }
}
