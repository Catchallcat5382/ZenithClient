package com.zenithclient.client.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/**
 * Gradually rebuilds loaded chunk sections while Freecam is active.
 *
 * Existing chunks may have been compiled with normal cave/occlusion data before
 * Freecam was enabled. Rebuilding a few chunk columns per tick lets the
 * Freecam occlusion mixin reopen them without causing one enormous frame spike.
 */
public final class FreecamVisibility {
    private static final ArrayDeque<Long> QUEUE = new ArrayDeque<>();
    private static final Set<Long> QUEUED = new HashSet<>();
    private static boolean wasActive;
    private static int lastCameraChunkX = Integer.MIN_VALUE;
    private static int lastCameraChunkZ = Integer.MIN_VALUE;

    private FreecamVisibility() { }

    public static void tick(Minecraft mc, boolean active, double cameraX, double cameraZ) {
        if (mc.level == null) {
            clear();
            wasActive = false;
            return;
        }

        int cameraChunkX = ((int) Math.floor(cameraX)) >> 4;
        int cameraChunkZ = ((int) Math.floor(cameraZ)) >> 4;

        if (active && !wasActive) {
            scheduleAround(mc, mc.player == null ? cameraChunkX : mc.player.blockPosition().getX() >> 4,
                    mc.player == null ? cameraChunkZ : mc.player.blockPosition().getZ() >> 4);
            scheduleAround(mc, cameraChunkX, cameraChunkZ);
        } else if (!active && wasActive) {
            clear();
            if (mc.player != null) {
                scheduleAround(mc, mc.player.blockPosition().getX() >> 4,
                        mc.player.blockPosition().getZ() >> 4);
            }
        }

        if (active && (cameraChunkX != lastCameraChunkX || cameraChunkZ != lastCameraChunkZ)) {
            scheduleRing(mc, cameraChunkX, cameraChunkZ, 2);
            lastCameraChunkX = cameraChunkX;
            lastCameraChunkZ = cameraChunkZ;
        }

        process(mc, active ? 4 : 6);
        wasActive = active;
    }

    private static void scheduleAround(Minecraft mc, int centerX, int centerZ) {
        int radius = Math.max(2, Math.min(12, mc.options.renderDistance().get()));
        for (int ring = 0; ring <= radius; ring++) {
            scheduleRing(mc, centerX, centerZ, ring);
        }
    }

    private static void scheduleRing(Minecraft mc, int centerX, int centerZ, int ring) {
        if (ring == 0) {
            enqueue(centerX, centerZ);
            return;
        }
        for (int x = -ring; x <= ring; x++) {
            enqueue(centerX + x, centerZ - ring);
            enqueue(centerX + x, centerZ + ring);
        }
        for (int z = -ring + 1; z < ring; z++) {
            enqueue(centerX - ring, centerZ + z);
            enqueue(centerX + ring, centerZ + z);
        }
    }

    private static void enqueue(int chunkX, int chunkZ) {
        long key = (((long) chunkX) << 32) ^ (chunkZ & 0xFFFFFFFFL);
        if (QUEUED.add(key)) QUEUE.addLast(key);
    }

    private static void process(Minecraft mc, int columnsPerTick) {
        int minSection = levelMinY(mc) >> 4;
        int maxSection = (levelMaxY(mc) - 1) >> 4;

        for (int i = 0; i < columnsPerTick && !QUEUE.isEmpty(); i++) {
            long key = QUEUE.removeFirst();
            QUEUED.remove(key);
            int chunkX = (int) (key >> 32);
            int chunkZ = (int) key;

            try {
                for (int sectionY = minSection; sectionY <= maxSection; sectionY++) {
                    mc.level.setSectionDirtyWithNeighbors(chunkX, sectionY, chunkZ);
                }
            } catch (RuntimeException ignored) {
                // A chunk may unload while queued. Continue with the next one.
            }
        }
    }

    private static int levelMinY(Minecraft mc) {
        return invokeHeight(mc.level, "getMinY", "getMinBuildHeight", -64);
    }

    private static int levelMaxY(Minecraft mc) {
        return invokeHeight(mc.level, "getMaxY", "getMaxBuildHeight", 320);
    }

    private static int invokeHeight(Object level, String first, String second, int fallback) {
        for (String name : new String[]{first, second}) {
            try {
                Method method = level.getClass().getMethod(name);
                Object value = method.invoke(level);
                if (value instanceof Number number) return number.intValue();
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Try the next mapping.
            }
        }
        return fallback;
    }

    private static void clear() {
        QUEUE.clear();
        QUEUED.clear();
        lastCameraChunkX = Integer.MIN_VALUE;
        lastCameraChunkZ = Integer.MIN_VALUE;
    }
}
