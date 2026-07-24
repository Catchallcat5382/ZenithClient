package com.zenithclient.client.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.lang.reflect.Field;

/**
 * Compatibility access for the currently open Minecraft screen.
 *
 * Minecraft 26.2 no longer exposes the source-level mc.screen member used by
 * older mappings, but the runtime screen field remains available. Reflection
 * keeps this source compatible with the project's different mapping targets.
 */
public final class MinecraftScreenCompat {
    private MinecraftScreenCompat() { }

    public static Screen currentScreen(Minecraft client) {
        if (client == null) return null;

        try {
            Field field = Minecraft.class.getField("screen");
            Object value = field.get(client);
            return value instanceof Screen screen ? screen : null;
        } catch (NoSuchFieldException | IllegalAccessException ignored) {
            return null;
        }
    }

    public static boolean hasOpenScreen(Minecraft client) {
        return currentScreen(client) != null;
    }
}
