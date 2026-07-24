package com.zenithclient.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Projects real world coordinates through the active Minecraft camera.
 * Labels, tracers, block boxes and trajectory points remain attached to their
 * entities/blocks instead of inheriting the player's HUD position or Y level.
 */
public final class ScreenSpaceVisualRenderer {
    private ScreenSpaceVisualRenderer() { }

    public static void render(GuiGraphicsExtractor graphics, Minecraft client, DeltaTracker deltaTracker,
                              ZenithConfig config, List<BlockPos> highlightedBlocks,
                              List<BlockPos> xrayOutlineBlocks) {
        if (client.player == null || client.level == null) return;

        float tickDelta = deltaTracker.getGameTimeDeltaPartialTick(true);
        Projection projection = new Projection(client, tickDelta);
        renderEntityOverlays(graphics, client, config, projection, tickDelta);
        if (config.blockHighlights) renderBlocks(graphics, config, projection, highlightedBlocks);
        if (config.xray) renderXrayOutlines(graphics, client, projection, xrayOutlineBlocks);
        if (config.trajectoryPreview) renderTrajectory(graphics, client, config, projection);
    }

    private static void renderEntityOverlays(GuiGraphicsExtractor graphics, Minecraft client,
                                             ZenithConfig config, Projection projection, float tickDelta) {
        double rangeSquared = (double) config.entityRange * config.entityRange;
        int rendered = 0;

        for (Entity entity : client.level.entitiesForRendering()) {
            if (rendered >= 96) break;
            if (entity == client.player || entity.distanceToSqr(client.player) > rangeSquared) continue;

            boolean player = entity instanceof Player;
            boolean item = entity instanceof ItemEntity;
            boolean projectile = entity instanceof Projectile;
            if (player && !config.playerEsp) continue;
            if (item && !config.itemEsp) continue;
            if (projectile && !config.projectileEsp) continue;
            if (!player && !item && !projectile
                    && (!config.entityHighlights || !ZenithClient.matchesEntityMode(entity))) continue;

            AABB box = lerpedBox(entity, tickDelta);
            ScreenPoint[] corners = projectCorners(projection, box);
            ScreenRect rect = bounds(corners);

            // Always derive the label from the entity's interpolated world-space top-center.
            Vec3 labelWorld = new Vec3(
                    (box.minX + box.maxX) * 0.5,
                    box.maxY + Math.max(0.18, entity.getBbHeight() * 0.08),
                    (box.minZ + box.maxZ) * 0.5
            );
            ScreenPoint labelAnchor = projection.project(labelWorld);
            if (labelAnchor == null && rect != null) {
                labelAnchor = new ScreenPoint((rect.minX + rect.maxX) / 2, rect.minY);
            }
            if (labelAnchor == null) continue;

            int color = player ? config.playerOutlineColor
                    : item ? config.itemEspColor
                    : projectile ? config.projectileEspColor
                    : config.entityOutlineColor;
            boolean tracers = player ? config.playerTracers
                    : item ? config.itemTracers
                    : projectile ? config.projectileTracers
                    : config.entityTracers;
            boolean labels = player ? config.playerNameTags : item || projectile || config.entityNameTags;

            if (tracers) {
                ScreenPoint from = new ScreenPoint(projection.width / 2, projection.height - 2);
                ScreenPoint target = rect == null
                        ? labelAnchor
                        : new ScreenPoint((rect.minX + rect.maxX) / 2, rect.maxY);
                drawLine(graphics, from, target, withAlpha(color, 220), 1);
            }

            if (labels) {
                String name = entity.getName().getString();
                int textX = labelAnchor.x - client.font.width(name) / 2;
                int textY = labelAnchor.y - 10;
                // Do not clamp to the player's screen Y. Off-screen entities simply do not render.
                if (textY >= -20 && textY <= projection.height + 20) {
                    graphics.text(client.font, name, textX, textY, withAlpha(color, 255), true);
                }
            }
            rendered++;
        }
    }

    private static AABB lerpedBox(Entity entity, float tickDelta) {
        double x = lerp(entity.xo, entity.getX(), tickDelta);
        double y = lerp(entity.yo, entity.getY(), tickDelta);
        double z = lerp(entity.zo, entity.getZ(), tickDelta);
        return entity.getBoundingBox().move(x - entity.getX(), y - entity.getY(), z - entity.getZ());
    }

    private static double lerp(double from, double to, float delta) {
        return from + (to - from) * delta;
    }

    private static void renderBlocks(GuiGraphicsExtractor graphics, ZenithConfig config,
                                     Projection projection, List<BlockPos> blocks) {
        int outline = withAlpha(config.blockOutlineColor, 255);
        int rendered = 0;
        for (BlockPos pos : blocks) {
            if (rendered++ >= 768) break;
            ScreenPoint[] corners = projectCorners(projection,
                    new AABB(pos.getX(), pos.getY(), pos.getZ(),
                            pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0));
            if (bounds(corners) == null) continue;
            drawProjectedBox(graphics, corners, outline, 1);
        }
    }

    // X-ray is implemented by renderer hooks in XrayHooks and ModelBlockRendererMixin.
    // The old screen-space ore outline scanner was intentionally removed because it
    // scanned tens of thousands of blocks and duplicated the real renderer X-ray.
    private static void renderXrayOutlines(GuiGraphicsExtractor graphics, Minecraft client,
                                           Projection projection, List<BlockPos> blocks) {
        // No-op by design.
    }

    private static void renderTrajectory(GuiGraphicsExtractor graphics, Minecraft client,
                                         ZenithConfig config, Projection projection) {
        ItemStack held = supportedProjectile(client.player.getMainHandItem())
                ? client.player.getMainHandItem() : client.player.getOffhandItem();
        if (!supportedProjectile(held)) {
            ZenithClient.setTrajectoryTarget(null);
            return;
        }

        double speed;
        double gravity;
        double drag = 0.99;
        double pitchOffset = 0.0;
        if (held.is(Items.BOW)) {
            float power = 1.0F;
            if (client.player.isUsingItem() && client.player.getUseItem().is(Items.BOW)) {
                float t = client.player.getTicksUsingItem() / 20.0F;
                power = Math.min((t * t + t * 2.0F) / 3.0F, 1.0F);
            }
            if (power < 0.1F) return;
            speed = power * 3.0;
            gravity = 0.05;
        } else if (held.is(Items.TRIDENT)) {
            speed = 2.5;
            gravity = 0.05;
        } else if (held.is(Items.EXPERIENCE_BOTTLE)) {
            speed = 0.7;
            gravity = 0.07;
            pitchOffset = -20.0;
        } else if (held.is(Items.SPLASH_POTION) || held.is(Items.LINGERING_POTION)) {
            speed = 0.5;
            gravity = 0.05;
            pitchOffset = -20.0;
        } else {
            speed = 1.5;
            gravity = 0.03;
        }

        double yaw = Math.toRadians(client.player.getYRot());
        double pitch = Math.toRadians(client.player.getXRot() + pitchOffset);
        Vec3 look = new Vec3(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch),
                Math.cos(yaw) * Math.cos(pitch)).normalize();
        Vec3 origin = client.player.getEyePosition().add(0.0, -0.10, 0.0);
        Vec3 position = origin;
        Vec3 motion = client.player.getDeltaMovement();
        Vec3 velocity = look.scale(speed).add(motion.x, client.player.onGround() ? 0.0 : motion.y, motion.z);

        List<Entity> collisionEntities = new ArrayList<>();
        for (Entity entity : client.level.entitiesForRendering()) {
            if (entity != client.player && entity instanceof LivingEntity && entity.isAlive()
                    && entity.distanceToSqr(client.player) <= 128.0 * 128.0) {
                collisionEntities.add(entity);
            }
            if (collisionEntities.size() >= 96) break;
        }

        ScreenPoint previous = null;
        Vec3 impact = position;
        Entity predictedTarget = null;
        int color = withAlpha(config.trajectoryColor, 255);
        int thickness = Math.max(1, Math.min(4, config.trajectoryThickness));
        int substeps = Math.max(2, Math.min(12, config.lineDensity * 2));
        double startDistanceSquared = config.trajectoryStartDistance * config.trajectoryStartDistance;
        boolean stopped = false;

        for (int tick = 0; tick < 160 && !stopped; tick++) {
            for (int sub = 0; sub < substeps; sub++) {
                Vec3 next = position.add(velocity.scale(1.0 / substeps));
                BlockHitResult blockHit = client.level.clip(new ClipContext(position, next,
                        ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, client.player));
                Vec3 segmentEnd = blockHit.getType() == HitResult.Type.MISS ? next : blockHit.getLocation();
                double closest = position.distanceToSqr(segmentEnd);
                Entity hitEntity = null;

                for (Entity entity : collisionEntities) {
                    java.util.Optional<Vec3> clipped = entity.getBoundingBox().inflate(0.30).clip(position, segmentEnd);
                    if (clipped.isEmpty()) continue;
                    double distance = position.distanceToSqr(clipped.get());
                    if (distance <= closest) {
                        closest = distance;
                        segmentEnd = clipped.get();
                        hitEntity = entity;
                    }
                }

                impact = segmentEnd;
                if (origin.distanceToSqr(segmentEnd) >= startDistanceSquared) {
                    ScreenPoint point = projection.project(segmentEnd);
                    if (previous != null && point != null) drawLine(graphics, previous, point, color, thickness);
                    if (point != null) previous = point;
                }
                if (hitEntity != null) predictedTarget = hitEntity;
                if (hitEntity != null || blockHit.getType() != HitResult.Type.MISS) {
                    stopped = true;
                    break;
                }
                position = next;
            }
            velocity = velocity.scale(drag).add(0.0, -gravity, 0.0);
        }

        ZenithClient.setTrajectoryTarget(predictedTarget);
        ScreenPoint end = projection.project(impact);
        if (end != null) drawImpactBox(graphics, end, color, thickness);
    }

    private static boolean supportedProjectile(ItemStack stack) {
        return stack.is(Items.BOW) || stack.is(Items.TRIDENT) || stack.is(Items.SNOWBALL)
                || stack.is(Items.EGG) || stack.is(Items.ENDER_PEARL)
                || stack.is(Items.EXPERIENCE_BOTTLE) || stack.is(Items.SPLASH_POTION)
                || stack.is(Items.LINGERING_POTION);
    }

    private static ScreenPoint[] projectCorners(Projection projection, AABB box) {
        return new ScreenPoint[]{
                projection.project(new Vec3(box.minX, box.minY, box.minZ)),
                projection.project(new Vec3(box.maxX, box.minY, box.minZ)),
                projection.project(new Vec3(box.maxX, box.minY, box.maxZ)),
                projection.project(new Vec3(box.minX, box.minY, box.maxZ)),
                projection.project(new Vec3(box.minX, box.maxY, box.minZ)),
                projection.project(new Vec3(box.maxX, box.maxY, box.minZ)),
                projection.project(new Vec3(box.maxX, box.maxY, box.maxZ)),
                projection.project(new Vec3(box.minX, box.maxY, box.maxZ))
        };
    }

    private static ScreenRect bounds(ScreenPoint[] points) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int visible = 0;
        for (ScreenPoint point : points) {
            if (point == null) continue;
            visible++;
            minX = Math.min(minX, point.x);
            minY = Math.min(minY, point.y);
            maxX = Math.max(maxX, point.x);
            maxY = Math.max(maxY, point.y);
        }
        return visible < 2 ? null : new ScreenRect(minX, minY, maxX, maxY);
    }

    private static void drawProjectedBox(GuiGraphicsExtractor graphics, ScreenPoint[] points,
                                         int color, int thickness) {
        int[][] edges = {{0,1},{1,2},{2,3},{3,0},{4,5},{5,6},{6,7},{7,4},{0,4},{1,5},{2,6},{3,7}};
        for (int[] edge : edges) {
            ScreenPoint a = points[edge[0]];
            ScreenPoint b = points[edge[1]];
            if (a != null && b != null) drawLine(graphics, a, b, color, thickness);
        }
    }

    private static void drawImpactBox(GuiGraphicsExtractor graphics, ScreenPoint point,
                                      int color, int thickness) {
        int size = 6 + thickness;
        graphics.fill(point.x - size, point.y - size, point.x + size + 1, point.y - size + thickness, color);
        graphics.fill(point.x - size, point.y + size - thickness + 1, point.x + size + 1, point.y + size + 1, color);
        graphics.fill(point.x - size, point.y - size, point.x - size + thickness, point.y + size + 1, color);
        graphics.fill(point.x + size - thickness + 1, point.y - size, point.x + size + 1, point.y + size + 1, color);
    }

    private static void drawLine(GuiGraphicsExtractor graphics, ScreenPoint a, ScreenPoint b,
                                 int color, int thickness) {
        int dx = b.x - a.x;
        int dy = b.y - a.y;
        int steps = Math.max(Math.abs(dx), Math.abs(dy));
        if (steps <= 0) return;
        int radius = Math.max(0, thickness - 1);
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            int x = (int) Math.round(a.x + dx * t);
            int y = (int) Math.round(a.y + dy * t);
            graphics.fill(x - radius, y - radius, x + radius + 1, y + radius + 1, color);
        }
    }

    private static int withAlpha(int rgb, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (rgb & 0x00FFFFFF);
    }

    private record ScreenPoint(int x, int y) { }
    private record ScreenRect(int minX, int minY, int maxX, int maxY) { }

    private static final class Projection {
        private final Vec3 origin;
        private final Vec3 forward;
        private final Vec3 right;
        private final Vec3 up;
        private final int width;
        private final int height;
        private final double focalX;
        private final double focalY;

        private Projection(Minecraft client, float tickDelta) {
            net.minecraft.client.Camera camera = activeCamera(client);

            if (camera != null) {
                this.origin = camera.position();
                double yaw = Math.toRadians(camera.yRot());
                double pitch = Math.toRadians(camera.xRot());
                this.forward = direction(yaw, pitch);
            } else {
                // Never use a new blank Camera at world origin. Use the player's real interpolated camera.
                double x = lerp(client.player.xo, client.player.getX(), tickDelta);
                double y = lerp(client.player.yo, client.player.getY(), tickDelta) + client.player.getEyeHeight();
                double z = lerp(client.player.zo, client.player.getZ(), tickDelta);
                this.origin = new Vec3(x, y, z);
                this.forward = direction(Math.toRadians(client.player.getYRot()),
                        Math.toRadians(client.player.getXRot()));
            }

            Vec3 worldUp = new Vec3(0.0, 1.0, 0.0);
            Vec3 calculatedRight = forward.cross(worldUp);
            if (calculatedRight.lengthSqr() < 1.0E-8) calculatedRight = new Vec3(1.0, 0.0, 0.0);
            this.right = calculatedRight.normalize();
            this.up = right.cross(forward).normalize();

            this.width = client.getWindow().getGuiScaledWidth();
            this.height = client.getWindow().getGuiScaledHeight();
            double verticalFov = Math.toRadians(Math.max(1.0, Math.min(179.0, client.options.fov().get())));
            this.focalY = (height * 0.5) / Math.tan(verticalFov * 0.5);
            this.focalX = focalY; // square GUI pixels; aspect is already represented by width/height centers.
        }

        private static Vec3 direction(double yaw, double pitch) {
            return new Vec3(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch),
                    Math.cos(yaw) * Math.cos(pitch)).normalize();
        }

        private static net.minecraft.client.Camera activeCamera(Minecraft client) {
            for (String methodName : new String[]{"mainCamera", "getMainCamera"}) {
                try {
                    Object value = client.gameRenderer.getClass().getMethod(methodName).invoke(client.gameRenderer);
                    if (value instanceof net.minecraft.client.Camera camera) return camera;
                } catch (ReflectiveOperationException ignored) {
                    // Try declared methods and then fields.
                }
                try {
                    java.lang.reflect.Method method = client.gameRenderer.getClass().getDeclaredMethod(methodName);
                    method.setAccessible(true);
                    Object value = method.invoke(client.gameRenderer);
                    if (value instanceof net.minecraft.client.Camera camera) return camera;
                } catch (ReflectiveOperationException ignored) {
                    // Continue.
                }
            }

            for (java.lang.reflect.Field field : client.gameRenderer.getClass().getDeclaredFields()) {
                if (!net.minecraft.client.Camera.class.isAssignableFrom(field.getType())) continue;
                try {
                    field.setAccessible(true);
                    Object value = field.get(client.gameRenderer);
                    if (value instanceof net.minecraft.client.Camera camera) return camera;
                } catch (ReflectiveOperationException ignored) {
                    // Continue to player fallback.
                }
            }
            return null;
        }

        private ScreenPoint project(Vec3 world) {
            Vec3 delta = world.subtract(origin);
            double depth = delta.dot(forward);
            if (depth <= 0.05) return null;

            double screenX = width * 0.5 + (delta.dot(right) / depth) * focalX;
            double screenY = height * 0.5 - (delta.dot(up) / depth) * focalY;
            if (!Double.isFinite(screenX) || !Double.isFinite(screenY)) return null;
            if (screenX < -width * 0.25 || screenX > width * 1.25
                    || screenY < -height * 0.25 || screenY > height * 1.25) return null;
            return new ScreenPoint((int) Math.round(screenX), (int) Math.round(screenY));
        }
    }
}
