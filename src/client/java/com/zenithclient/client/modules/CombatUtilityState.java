package com.zenithclient.client.modules;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;

/** Persistent settings and key handling for EXP Thrower. */
public final class CombatUtilityState {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir()
            .resolve("zenithclient-combat-utilities.json");

    private static State state = load();
    private static boolean expKeyWasDown;

    private CombatUtilityState() { }

    public static boolean expThrowerEnabled() { return state.expThrowerEnabled; }
    public static int expDelayTicks() { return state.expDelayTicks; }
    public static boolean expSwapBack() { return state.expSwapBack; }
    public static int expKey() { return state.expKey; }

    public static void setExpThrowerEnabled(boolean value) {
        state.expThrowerEnabled = value;
        if (!value) ExpThrowerController.onDisabled();
        save();
    }

    public static void toggleExpThrower() {
        setExpThrowerEnabled(!state.expThrowerEnabled);
    }

    public static void setExpDelayTicks(int value) {
        state.expDelayTicks = clamp(value, 1, 20);
        save();
    }

    public static void setExpSwapBack(boolean value) {
        state.expSwapBack = value;
        save();
    }

    public static void setExpKey(int key) {
        state.expKey = key;
        save();
    }

    public static void handleKeybinds(Minecraft mc) {
        if (mc.player == null || mc.level == null
                || MinecraftScreenCompat.hasOpenScreen(mc)) {
            expKeyWasDown = false;
            return;
        }

        long window = mc.getWindow().handle();
        boolean expDown = state.expKey >= 0
                && GLFW.glfwGetKey(window, state.expKey) == GLFW.GLFW_PRESS;

        if (expDown && !expKeyWasDown) toggleExpThrower();
        expKeyWasDown = expDown;
    }

    private static State load() {
        try {
            if (Files.exists(PATH)) {
                State loaded = GSON.fromJson(Files.readString(PATH), State.class);
                if (loaded != null) {
                    loaded.sanitize();
                    return loaded;
                }
            }
        } catch (Exception ignored) {
            // Use defaults.
        }
        return new State();
    }

    private static void save() {
        state.sanitize();
        try {
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, GSON.toJson(state));
        } catch (Exception exception) {
            System.err.println("ZenithClient could not save EXP Thrower settings: "
                    + exception.getMessage());
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class State {
        boolean expThrowerEnabled;
        int expDelayTicks = 1;
        boolean expSwapBack = true;
        int expKey = -1;

        void sanitize() {
            expDelayTicks = clamp(expDelayTicks, 1, 20);
            if (expKey < -1) expKey = -1;
        }
    }
}
