package com.zenithclient.client.modules;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Small client-only visual utilities inspired by common render modules.
 * These do not send combat, placement, inventory, or movement packets.
 */
public final class VisualExtrasState {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir()
            .resolve("zenithclient-visual-extras.json");
    private static State state = load();
    private static Number savedFov;

    private VisualExtrasState() { }

    public static boolean zoom() { return state.zoom; }
    public static boolean clearWeather() { return state.clearWeather; }
    public static boolean daylight() { return state.daylight; }
    public static boolean noHurtCamera() { return state.noHurtCamera; }
    public static boolean noPortalOverlay() { return state.noPortalOverlay; }

    public static void toggleZoom() { state.zoom = !state.zoom; save(); }
    public static void toggleClearWeather() { state.clearWeather = !state.clearWeather; save(); }
    public static void toggleDaylight() { state.daylight = !state.daylight; save(); }
    public static void toggleNoHurtCamera() { state.noHurtCamera = !state.noHurtCamera; save(); }
    public static void toggleNoPortalOverlay() { state.noPortalOverlay = !state.noPortalOverlay; save(); }

    public static void tick(Minecraft mc) {
        updateZoom(mc);

        if (mc.level != null) {
            if (state.clearWeather) {
                invokeNumber(mc.level, "setRainLevel", 0.0F);
                invokeNumber(mc.level, "setThunderLevel", 0.0F);
                setNumberField(mc.level, "rainLevel", 0.0F);
                setNumberField(mc.level, "oRainLevel", 0.0F);
                setNumberField(mc.level, "thunderLevel", 0.0F);
                setNumberField(mc.level, "oThunderLevel", 0.0F);
            }
            if (state.daylight) {
                invokeNumber(mc.level, "setDayTime", 6000L);
                setNumberField(mc.level, "dayTime", 6000L);
            }
        }

        if (mc.player != null) {
            if (state.noHurtCamera) {
                setNumberField(mc.player, "hurtTime", 0);
                setNumberField(mc.player, "hurtDuration", 0);
                setNumberField(mc.player, "hurtDir", 0.0F);
            }
            if (state.noPortalOverlay) {
                setNumberField(mc.player, "portalTime", 0.0F);
                setNumberField(mc.player, "oPortalTime", 0.0F);
            }
        }
    }

    private static void updateZoom(Minecraft mc) {
        Object option = invokeNoArgs(mc.options, "fov");
        if (option == null) return;

        if (state.zoom) {
            if (savedFov == null) {
                Object value = invokeNoArgs(option, "get");
                if (value instanceof Number number) savedFov = number;
            }
            setOption(option, 30);
        } else if (savedFov != null) {
            setOption(option, savedFov);
            savedFov = null;
        }
    }

    private static void setOption(Object option, Object requested) {
        for (Method method : option.getClass().getMethods()) {
            if (!method.getName().equals("set") || method.getParameterCount() != 1) continue;
            try {
                Class<?> type = method.getParameterTypes()[0];
                method.invoke(option, convertNumber(requested, type));
                return;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Try another overload.
            }
        }
    }

    private static void invokeNumber(Object target, String name, Number value) {
        for (Method method : target.getClass().getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != 1) continue;
            try {
                method.invoke(target, convertNumber(value, method.getParameterTypes()[0]));
                return;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Optional visual method.
            }
        }
    }

    private static Object invokeNoArgs(Object target, String name) {
        try {
            return target.getClass().getMethod(name).invoke(target);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static void setNumberField(Object target, String name, Number value) {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, convertNumber(value, field.getType()));
                return;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                type = type.getSuperclass();
            }
        }
    }

    private static Object convertNumber(Object value, Class<?> type) {
        if (!(value instanceof Number number)) return value;
        if (type == int.class || type == Integer.class) return number.intValue();
        if (type == long.class || type == Long.class) return number.longValue();
        if (type == float.class || type == Float.class) return number.floatValue();
        if (type == double.class || type == Double.class) return number.doubleValue();
        if (type == short.class || type == Short.class) return number.shortValue();
        if (type == byte.class || type == Byte.class) return number.byteValue();
        return value;
    }

    private static State load() {
        try {
            if (Files.exists(PATH)) {
                State loaded = GSON.fromJson(Files.readString(PATH), State.class);
                if (loaded != null) return loaded;
            }
        } catch (Exception ignored) {
            // Fall back to defaults.
        }
        return new State();
    }

    private static void save() {
        try {
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, GSON.toJson(state));
        } catch (Exception ignored) {
            // A config write failure should never crash the client.
        }
    }

    private static final class State {
        boolean zoom;
        boolean clearWeather;
        boolean daylight;
        boolean noHurtCamera;
        boolean noPortalOverlay;
    }
}
