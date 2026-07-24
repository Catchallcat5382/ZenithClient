package com.zenithclient.client.modules;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

/** Persistent preset switches layered on top of the existing Entity ESP renderer. */
public final class EspPresetState {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir()
            .resolve("zenithclient-esp-presets.json");
    private static State state = load();

    private EspPresetState() { }

    public static boolean hostile() { return state.hostile; }
    public static boolean passive() { return state.passive; }
    public static boolean living() { return state.living; }

    public static void toggleHostile() { state.hostile = !state.hostile; save(); }
    public static void togglePassive() { state.passive = !state.passive; save(); }
    public static void toggleLiving() { state.living = !state.living; save(); }

    private static State load() {
        try {
            if (Files.exists(PATH)) {
                State loaded = GSON.fromJson(Files.readString(PATH), State.class);
                if (loaded != null) return loaded;
            }
        } catch (Exception ignored) {
            // Use defaults.
        }
        return new State();
    }

    private static void save() {
        try {
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, GSON.toJson(state));
        } catch (Exception exception) {
            System.err.println("ZenithClient could not save ESP presets: "
                    + exception.getMessage());
        }
    }

    private static final class State {
        boolean hostile;
        boolean passive;
        boolean living;
    }
}
