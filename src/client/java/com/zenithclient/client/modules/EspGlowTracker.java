package com.zenithclient.client.modules;

import com.zenithclient.client.ZenithClient;
import com.zenithclient.client.ZenithConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Tracks entities whose glowing result was overridden by ZenithClient. */
public final class EspGlowTracker {
    private static final Set<UUID> CONTROLLED = ConcurrentHashMap.newKeySet();
    private static Object lastLevel;

    private EspGlowTracker() { }

    public static boolean resolveGlow(Entity entity, boolean vanillaGlow) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            CONTROLLED.clear();
            lastLevel = mc.level;
        }

        boolean target = isTarget(entity);
        UUID id = entity.getUUID();
        if (target) {
            CONTROLLED.add(id);
            return true;
        }

        // Explicitly clear only outlines that ZenithClient previously owned.
        // Vanilla/team glowing for unrelated entities remains untouched.
        if (CONTROLLED.remove(id)) return false;
        return vanillaGlow;
    }

    public static int color(Entity entity) {
        if (!isTarget(entity)) return -1;
        ZenithConfig config = ZenithClient.getConfig();
        if (ZenithClient.isTrajectoryTarget(entity)) return config.trajectoryColor & 0xFFFFFF;
        if (entity instanceof Player && config.playerEsp) return config.playerOutlineColor & 0xFFFFFF;
        if (entity instanceof ItemEntity && config.itemEsp) return config.itemEspColor & 0xFFFFFF;
        if (entity instanceof Projectile && config.projectileEsp) return config.projectileEspColor & 0xFFFFFF;
        return config.entityOutlineColor & 0xFFFFFF;
    }

    private static boolean isTarget(Entity entity) {
        Minecraft mc = Minecraft.getInstance();
        if (entity == null || mc.player == null || mc.level == null || entity == mc.player) return false;

        ZenithConfig config = ZenithClient.getConfig();
        if (entity.distanceToSqr(mc.player) > (double) config.entityRange * config.entityRange) return false;
        if (ZenithClient.isTrajectoryTarget(entity)) return true;

        boolean selectedByEntityEsp = config.entityHighlights && selected(entity, config.entitySearch);
        if (entity instanceof Player) return config.playerEsp || selectedByEntityEsp;
        if (entity instanceof ItemEntity) return config.itemEsp || selectedByEntityEsp;
        if (entity instanceof Projectile) return config.projectileEsp || selectedByEntityEsp;
        return selectedByEntityEsp;
    }

    private static boolean selected(Entity entity, String raw) {
        if (raw == null || raw.isBlank()) return false;
        String id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString().toLowerCase(Locale.ROOT);
        for (String token : raw.split(",")) {
            String wanted = token.trim().toLowerCase(Locale.ROOT);
            if (wanted.isEmpty()) continue;
            if (id.equals(wanted) || id.endsWith(":" + wanted) || id.contains(wanted)) return true;
        }
        return false;
    }
}
