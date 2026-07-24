package com.zenithclient.client.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;

import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * Collects every entity type known by the client plus entity types that are
 * actually present in the current world. This includes modded entity types
 * registered on the client and runtime types observed from the server/world.
 */
public final class DynamicEntityCatalog {
    private DynamicEntityCatalog() { }

    public record Snapshot(Set<String> all, Set<String> live) { }

    public static Snapshot collect(String savedIds) {
        TreeSet<String> all = new TreeSet<>();
        LinkedHashSet<String> live = new LinkedHashSet<>();

        BuiltInRegistries.ENTITY_TYPE.keySet().forEach(id ->
                all.add(id.toString().toLowerCase(Locale.ROOT)));

        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            for (Entity entity : mc.level.entitiesForRendering()) {
                String id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType())
                        .toString().toLowerCase(Locale.ROOT);
                all.add(id);
                live.add(id);
            }

            // Some mod loaders expose additional registry views through the
            // active level. Use reflection so this remains mapping tolerant.
            collectRegistryKeysReflectively(mc.level, all);
        }

        if (savedIds != null && !savedIds.isBlank()) {
            for (String token : savedIds.split(",")) {
                String id = token.trim().toLowerCase(Locale.ROOT);
                if (!id.isEmpty()) all.add(id);
            }
        }

        return new Snapshot(new LinkedHashSet<>(all), live);
    }

    private static void collectRegistryKeysReflectively(Object level, Set<String> output) {
        try {
            Method registryAccess = level.getClass().getMethod("registryAccess");
            Object access = registryAccess.invoke(level);
            if (access == null) return;

            for (Method method : access.getClass().getMethods()) {
                if (method.getParameterCount() != 0) continue;
                String name = method.getName().toLowerCase(Locale.ROOT);
                if (!name.contains("registr") && !name.contains("lookup")) continue;

                Object result;
                try {
                    result = method.invoke(access);
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    continue;
                }
                collectKeys(result, output);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // BuiltInRegistries plus live world entities remain available.
        }
    }

    private static void collectKeys(Object value, Set<String> output) {
        if (value == null) return;
        try {
            for (Method method : value.getClass().getMethods()) {
                if (method.getParameterCount() != 0) continue;
                String name = method.getName();
                if (!name.equals("keySet") && !name.equals("registryKeySet")) continue;
                Object keys = method.invoke(value);
                if (!(keys instanceof Iterable<?> iterable)) continue;
                for (Object key : iterable) {
                    String text = String.valueOf(key).toLowerCase(Locale.ROOT);
                    if (text.contains(":") && !text.contains(" ")) output.add(text);
                }
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Optional discovery path only.
        }
    }
}
