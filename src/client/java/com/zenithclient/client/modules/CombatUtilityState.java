package com.zenithclient.client.modules;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Persistent settings and key handling for the normal-action combat utilities.
 *
 * These modules use ordinary hotbar selection and use-item/attack paths. They
 * do not fabricate movement packets or attempt to bypass server validation.
 */
public final class CombatUtilityState {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir()
            .resolve("zenithclient-combat-utilities.json");

    private static State state = load();
    private static boolean breachKeyWasDown;
    private static boolean expKeyWasDown;

    private CombatUtilityState() { }

    public static boolean breachSwapEnabled() { return state.breachSwapEnabled; }
    public static boolean breachOnlyArmored() { return state.breachOnlyArmored; }
    public static int breachRestoreDelay() { return state.breachRestoreDelay; }
    public static int breachKey() { return state.breachKey; }

    public static boolean expThrowerEnabled() { return state.expThrowerEnabled; }
    public static int expDelayTicks() { return state.expDelayTicks; }
    public static boolean expLookDown() { return state.expLookDown; }
    public static boolean expSwapBack() { return state.expSwapBack; }
    public static int expKey() { return state.expKey; }

    public static void setBreachSwapEnabled(boolean value) {
        state.breachSwapEnabled = value;
        if (!value) BreachSwapController.reset();
        save();
    }

    public static void toggleBreachSwap() {
        setBreachSwapEnabled(!state.breachSwapEnabled);
    }

    public static void setBreachOnlyArmored(boolean value) {
        state.breachOnlyArmored = value;
        save();
    }

    public static void setBreachRestoreDelay(int value) {
        state.breachRestoreDelay = clamp(value, 0, 20);
        save();
    }

    public static void setBreachKey(int key) {
        state.breachKey = key;
        save();
    }

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

    public static void setExpLookDown(boolean value) {
        state.expLookDown = value;
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
        if (mc.player == null || mc.level == null || MinecraftScreenCompat.hasOpenScreen(mc)) {
            breachKeyWasDown = false;
            expKeyWasDown = false;
            return;
        }

        long window = mc.getWindow().handle();

        boolean breachDown = isDown(window, state.breachKey);
        if (breachDown && !breachKeyWasDown) toggleBreachSwap();
        breachKeyWasDown = breachDown;

        boolean expDown = isDown(window, state.expKey);
        if (expDown && !expKeyWasDown) toggleExpThrower();
        expKeyWasDown = expDown;
    }

    private static boolean isDown(long window, int key) {
        return key >= 0 && GLFW.glfwGetKey(window, key) == GLFW.GLFW_PRESS;
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
            // Fall through to defaults.
        }
        return new State();
    }

    private static void save() {
        state.sanitize();
        try {
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, GSON.toJson(state));
        } catch (Exception exception) {
            System.err.println("ZenithClient could not save combat utility settings: "
                    + exception.getMessage());
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class State {
        boolean breachSwapEnabled;
        boolean breachOnlyArmored = true;
        int breachRestoreDelay = 2;
        int breachKey = -1;

        boolean expThrowerEnabled;
        int expDelayTicks = 1;
        boolean expLookDown = true;
        boolean expSwapBack = true;
        int expKey = -1;

        void sanitize() {
            breachRestoreDelay = clamp(breachRestoreDelay, 0, 20);
            expDelayTicks = clamp(expDelayTicks, 1, 20);
            if (breachKey < -1) breachKey = -1;
            if (expKey < -1) expKey = -1;
        }
    }
}
