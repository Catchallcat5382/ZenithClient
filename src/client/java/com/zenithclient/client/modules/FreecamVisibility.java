package com.zenithclient.client.modules;

import net.minecraft.client.Minecraft;

import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/** Progressive loaded-chunk visibility rebuilding for Freecam. */
public final class FreecamVisibility {
    private static final ArrayDeque<Long> QUEUE = new ArrayDeque<>();
    private static final Set<Long> QUEUED = new HashSet<>();
    private static int lastCameraChunkX = Integer.MIN_VALUE;
    private static int lastCameraChunkZ = Integer.MIN_VALUE;
    private static int refreshTicker;

    private FreecamVisibility() { }

    public static void resetForActivation(
            Minecraft mc,
            double cameraX,
            double cameraZ
    ) {
        clear();
        if (mc.level == null) return;

        int cameraChunkX = floorChunk(cameraX);
        int cameraChunkZ = floorChunk(cameraZ);

        if (mc.player != null) {
            scheduleAround(
                    mc,
                    mc.player.blockPosition().getX() >> 4,
                    mc.player.blockPosition().getZ() >> 4
            );
        }

        scheduleAround(mc, cameraChunkX, cameraChunkZ);
        lastCameraChunkX = cameraChunkX;
        lastCameraChunkZ = cameraChunkZ;
    }

    public static void resetAfterDeactivation(Minecraft mc) {
        clear();

        if (mc.level != null && mc.player != null) {
            scheduleAround(
                    mc,
                    mc.player.blockPosition().getX() >> 4,
                    mc.player.blockPosition().getZ() >> 4
            );
            process(mc, 12);
        }
    }

    public static void tick(
            Minecraft mc,
            boolean active,
            double cameraX,
            double cameraZ
    ) {
        if (!active || mc.level == null) return;

        int cameraChunkX = floorChunk(cameraX);
        int cameraChunkZ = floorChunk(cameraZ);

        if (cameraChunkX != lastCameraChunkX
                || cameraChunkZ != lastCameraChunkZ) {
            scheduleAround(mc, cameraChunkX, cameraChunkZ);
            lastCameraChunkX = cameraChunkX;
            lastCameraChunkZ = cameraChunkZ;
        }

        if (++refreshTicker >= 60) {
            refreshTicker = 0;
            scheduleAround(mc, cameraChunkX, cameraChunkZ);
        }

        process(mc, 10);
    }

    private static int floorChunk(double coordinate) {
        return ((int) Math.floor(coordinate)) >> 4;
    }

    private static void scheduleAround(
            Minecraft mc,
            int centerX,
            int centerZ
    ) {
        int radius = Math.max(
                2,
                Math.min(12, mc.options.renderDistance().get())
        );

        for (int ring = 0; ring <= radius; ring++) {
            scheduleRing(centerX, centerZ, ring);
        }
    }

    private static void scheduleRing(int centerX, int centerZ, int ring) {
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
        if (QUEUE.size() >= 8192) return;

        long key = (((long) chunkX) << 32)
                ^ (chunkZ & 0xFFFFFFFFL);

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
                for (int sectionY = minSection;
                     sectionY <= maxSection;
                     sectionY++) {
                    mc.level.setSectionDirtyWithNeighbors(
                            chunkX,
                            sectionY,
                            chunkZ
                    );
                }
            } catch (RuntimeException ignored) {
                // The chunk may unload while queued.
            }
        }
    }

    private static int levelMinY(Minecraft mc) {
        return invokeHeight(
                mc.level,
                "getMinY",
                "getMinBuildHeight",
                -64
        );
    }

    private static int levelMaxY(Minecraft mc) {
        return invokeHeight(
                mc.level,
                "getMaxY",
                "getMaxBuildHeight",
                320
        );
    }

    private static int invokeHeight(
            Object level,
            String first,
            String second,
            int fallback
    ) {
        for (String name : new String[]{first, second}) {
            try {
                Method method = level.getClass().getMethod(name);
                Object value = method.invoke(level);
                if (value instanceof Number number) {
                    return number.intValue();
                }
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
        refreshTicker = 0;
    }
}
