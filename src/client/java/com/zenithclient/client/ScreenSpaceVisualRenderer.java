package com.zenithclient.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Continuous HUD-projected geometry renderer.
 *
 * This intentionally avoids particles, vanilla glow, and unstable 26.2 GPU internals.
 * World points are projected into screen space, then drawn as connected pixels/rectangles.
 */
public final class ScreenSpaceVisualRenderer {
    private ScreenSpaceVisualRenderer() { }

    public static void render(GuiGraphicsExtractor graphics, Minecraft client, ZenithConfig config,
                              List<BlockPos> highlightedBlocks) {
        if (client.player == null || client.level == null) return;

        Projection projection = new Projection(client);
        renderEntities(graphics, client, config, projection);
        if (config.blockHighlights) renderBlocks(graphics, config, projection, highlightedBlocks);
        if (config.trajectoryPreview) renderTrajectory(graphics, client, config, projection);
    }

    private static void renderEntities(GuiGraphicsExtractor graphics, Minecraft client,
                                       ZenithConfig config, Projection projection) {
        double rangeSquared = (double) config.entityRange * config.entityRange;
        int rendered = 0;
        for (Entity entity : client.level.entitiesForRendering()) {
            if (rendered >= 128) break;
            if (entity == client.player || entity.distanceToSqr(client.player) > rangeSquared) continue;
            boolean player = entity instanceof Player;
            if (player && !config.playerEsp) continue;
            if (!player && (!config.entityHighlights || !ZenithClient.matchesEntityMode(entity))) continue;

            AABB espBox = entity.getBoundingBox().inflate(0.04);
            ScreenPoint[] corners = projectCorners(projection, espBox);
            ScreenRect rect = bounds(corners);
            if (rect == null || rect.width() < 2 || rect.height() < 3) continue;

            int outlineColor = player ? config.playerOutlineColor : config.entityOutlineColor;
            int fillColor = player ? config.playerFillColor : config.entityFillColor;
            int fillOpacity = player ? config.playerFillOpacity : config.entityFillOpacity;
            int thickness = player ? config.playerOutlineThickness : config.entityOutlineThickness;
            ZenithConfig.EspShape shape = player ? config.playerEspShape : config.entityEspShape;
            boolean fillEnabled = player || config.entityFill;
            boolean outlineEnabled = player || config.entityOutline;
            boolean tracers = player ? config.playerTracers : config.entityTracers;
            boolean nameTags = player ? config.playerNameTags : config.entityNameTags;

            if (fillEnabled && fillOpacity > 0) {
                graphics.fill(rect.minX, rect.minY, rect.maxX, rect.maxY,
                        withAlpha(fillColor, percentToAlpha(fillOpacity)));
            }
            if (outlineEnabled) {
                int outline = withAlpha(outlineColor, 255);
                int lineWidth = Math.max(1, Math.min(4, thickness));
                if (shape == ZenithConfig.EspShape.BOX_3D) drawProjectedBox(graphics, corners, outline, lineWidth);
                else if (shape == ZenithConfig.EspShape.CORNERS) drawCorners(graphics, rect, outline, lineWidth);
                else drawRect(graphics, rect, outline, lineWidth);
            }
            if (tracers) {
                ScreenPoint from = new ScreenPoint(projection.width / 2, projection.height - 2);
                ScreenPoint to = new ScreenPoint((rect.minX + rect.maxX) / 2, rect.maxY);
                drawLine(graphics, from, to, withAlpha(outlineColor, 210), 1);
            }
            if (nameTags) {
                String name = entity.getName().getString();
                int textX = (rect.minX + rect.maxX - client.font.width(name)) / 2;
                graphics.text(client.font, name, textX, Math.max(2, rect.minY - 10), withAlpha(outlineColor, 255), true);
            }
            rendered++;
        }
    }

    private static void renderBlocks(GuiGraphicsExtractor graphics, ZenithConfig config,
                                     Projection projection, List<BlockPos> blocks) {
        int outline = withAlpha(config.blockOutlineColor, 255);
        int effectiveOpacity = config.blockFillOpacity;
        int fill = withAlpha(config.blockFillColor, percentToAlpha(effectiveOpacity));
        for (BlockPos pos : blocks) {
            ScreenRect rect = projectBox(projection, new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0));
            if (rect == null || rect.width() < 2 || rect.height() < 2) continue;
            if (effectiveOpacity > 0) graphics.fill(rect.minX, rect.minY, rect.maxX, rect.maxY, fill);
            drawRect(graphics, rect, outline, 1);
        }
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
                float useTicks = client.player.getTicksUsingItem();
                power = Math.min(((useTicks / 20.0F) * (useTicks / 20.0F) + (useTicks / 20.0F) * 2.0F) / 3.0F, 1.0F);
            }
            if (power < 0.1F) return;
            speed = power * 3.0; gravity = 0.05;
        } else if (held.is(Items.TRIDENT)) { speed = 2.5; gravity = 0.05; }
        else if (held.is(Items.EXPERIENCE_BOTTLE)) { speed = 0.7; gravity = 0.07; pitchOffset = -20.0; }
        else if (held.is(Items.SPLASH_POTION) || held.is(Items.LINGERING_POTION)) { speed = 0.5; gravity = 0.05; pitchOffset = -20.0; }
        else { speed = 1.5; gravity = 0.03; }

        double yaw = Math.toRadians(client.player.getYRot());
        double pitch = Math.toRadians(client.player.getXRot() + pitchOffset);
        Vec3 look = new Vec3(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch)).normalize();
        Vec3 origin = client.player.getEyePosition().add(0.0, -0.10, 0.0);
        Vec3 position = origin;
        Vec3 playerMotion = client.player.getDeltaMovement();
        Vec3 velocity = look.scale(speed).add(playerMotion.x, client.player.onGround() ? 0.0 : playerMotion.y, playerMotion.z);
        ScreenPoint previous = null;
        Vec3 impact = position;
        int color = withAlpha(config.trajectoryColor, 255);
        int thickness = Math.max(1, Math.min(4, config.trajectoryThickness));
        double startDistanceSquared = config.trajectoryStartDistance * config.trajectoryStartDistance;
        int substeps = Math.max(2, Math.min(12, config.lineDensity * 2));
        boolean stopped = false;
        Entity predictedTarget = null;

        java.util.List<Entity> collisionEntities = new java.util.ArrayList<>();
        for (Entity e : client.level.entitiesForRendering()) {
            if (e != client.player && e instanceof LivingEntity && e.isAlive() && e.distanceToSqr(client.player) <= 128.0 * 128.0) collisionEntities.add(e);
            if (collisionEntities.size() >= 96) break;
        }

        for (int tick = 0; tick < 160 && !stopped; tick++) {
            for (int sub = 0; sub < substeps; sub++) {
                Vec3 next = position.add(velocity.scale(1.0 / substeps));
                BlockHitResult blockHit = client.level.clip(new ClipContext(position, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, client.player));
                Vec3 segmentEnd = blockHit.getType() == HitResult.Type.MISS ? next : blockHit.getLocation();
                double closest = position.distanceToSqr(segmentEnd);
                Entity hitEntity = null;

                for (Entity entity : collisionEntities) {
                    java.util.Optional<Vec3> clipped = entity.getBoundingBox().inflate(0.30).clip(position, segmentEnd);
                    if (clipped.isEmpty()) continue;
                    double distance = position.distanceToSqr(clipped.get());
                    if (distance <= closest) { closest = distance; segmentEnd = clipped.get(); hitEntity = entity; }
                }

                impact = segmentEnd;
                if (origin.distanceToSqr(segmentEnd) >= startDistanceSquared) {
                    ScreenPoint point = projection.project(segmentEnd);
                    if (previous != null && point != null) drawLine(graphics, previous, point, color, thickness);
                    if (point != null) previous = point;
                }
                if (hitEntity != null) predictedTarget = hitEntity;
                if (hitEntity != null || blockHit.getType() != HitResult.Type.MISS) { stopped = true; break; }
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
                || stack.is(Items.EGG) || stack.is(Items.ENDER_PEARL) || stack.is(Items.EXPERIENCE_BOTTLE)
                || stack.is(Items.SPLASH_POTION) || stack.is(Items.LINGERING_POTION);
    }

    private static void drawImpactBox(GuiGraphicsExtractor graphics, ScreenPoint point, int color, int thickness) {
        int size = 6 + thickness;
        ScreenRect rect = new ScreenRect(point.x - size, point.y - size, point.x + size, point.y + size);
        drawRect(graphics, rect, color, Math.max(1, thickness));
        graphics.fill(point.x - 1, point.y - 1, point.x + 2, point.y + 2, color);
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
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, visible = 0;
        for (ScreenPoint point : points) {
            if (point == null) continue;
            visible++; minX = Math.min(minX, point.x); minY = Math.min(minY, point.y);
            maxX = Math.max(maxX, point.x); maxY = Math.max(maxY, point.y);
        }
        return visible < 2 ? null : new ScreenRect(minX, minY, maxX, maxY);
    }

    private static void drawProjectedBox(GuiGraphicsExtractor graphics, ScreenPoint[] p, int color, int thickness) {
        int[][] edges = {{0,1},{1,2},{2,3},{3,0},{4,5},{5,6},{6,7},{7,4},{0,4},{1,5},{2,6},{3,7}};
        for (int[] edge : edges) if (p[edge[0]] != null && p[edge[1]] != null) drawLine(graphics, p[edge[0]], p[edge[1]], color, thickness);
    }

    private static ScreenRect projectBox(Projection projection, AABB box) {
        double[] xs = {box.minX, box.maxX};
        double[] ys = {box.minY, box.maxY};
        double[] zs = {box.minZ, box.maxZ};
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int visible = 0;

        for (double x : xs) for (double y : ys) for (double z : zs) {
            ScreenPoint point = projection.project(new Vec3(x, y, z));
            if (point == null) continue;
            visible++;
            minX = Math.min(minX, point.x);
            minY = Math.min(minY, point.y);
            maxX = Math.max(maxX, point.x);
            maxY = Math.max(maxY, point.y);
        }
        if (visible < 2) return null;
        return new ScreenRect(minX, minY, maxX, maxY);
    }

    private static void drawCorners(GuiGraphicsExtractor graphics, ScreenRect rect, int color, int thickness) {
        int corner = Math.max(4, Math.min(12, Math.min(rect.width(), rect.height()) / 4));
        graphics.fill(rect.minX, rect.minY, rect.minX + corner, rect.minY + thickness, color);
        graphics.fill(rect.minX, rect.minY, rect.minX + thickness, rect.minY + corner, color);
        graphics.fill(rect.maxX - corner, rect.minY, rect.maxX, rect.minY + thickness, color);
        graphics.fill(rect.maxX - thickness, rect.minY, rect.maxX, rect.minY + corner, color);
        graphics.fill(rect.minX, rect.maxY - thickness, rect.minX + corner, rect.maxY, color);
        graphics.fill(rect.minX, rect.maxY - corner, rect.minX + thickness, rect.maxY, color);
        graphics.fill(rect.maxX - corner, rect.maxY - thickness, rect.maxX, rect.maxY, color);
        graphics.fill(rect.maxX - thickness, rect.maxY - corner, rect.maxX, rect.maxY, color);
    }

    private static void drawRect(GuiGraphicsExtractor graphics, ScreenRect rect, int color, int thickness) {
        graphics.fill(rect.minX, rect.minY, rect.maxX + 1, rect.minY + thickness, color);
        graphics.fill(rect.minX, rect.maxY - thickness + 1, rect.maxX + 1, rect.maxY + 1, color);
        graphics.fill(rect.minX, rect.minY, rect.minX + thickness, rect.maxY + 1, color);
        graphics.fill(rect.maxX - thickness + 1, rect.minY, rect.maxX + 1, rect.maxY + 1, color);
    }

    private static void drawLine(GuiGraphicsExtractor graphics, ScreenPoint a, ScreenPoint b, int color, int thickness) {
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

    private static int percentToAlpha(int percent) {
        return Math.max(0, Math.min(255, Math.round(Math.max(0, Math.min(100, percent)) * 2.55F)));
    }

    private static int withAlpha(int rgb, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (rgb & 0x00FFFFFF);
    }

    private record ScreenPoint(int x, int y) { }
    private record ScreenRect(int minX, int minY, int maxX, int maxY) {
        int width() { return maxX - minX; }
        int height() { return maxY - minY; }
    }

    private static final class Projection {
        private final Vec3 origin;
        private final Vec3 forward;
        private final Vec3 right;
        private final Vec3 up;
        private final int width;
        private final int height;
        private final double focalLength;

        private Projection(Minecraft client) {
            // Minecraft 26.2 no longer exposes GameRenderer#getMainCamera in these mappings.
            // Use the local player's interpolated eye position and view rotation, which are
            // the same supported values used by the trajectory renderer in this project.
            this.origin = client.player.getEyePosition();
            double yaw = Math.toRadians(client.player.getYRot());
            double pitch = Math.toRadians(client.player.getXRot());
            this.forward = new Vec3(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch)).normalize();
            Vec3 worldUp = new Vec3(0.0, 1.0, 0.0);
            Vec3 computedRight = forward.cross(worldUp);
            if (computedRight.lengthSqr() < 1.0E-6) computedRight = new Vec3(1.0, 0.0, 0.0);
            this.right = computedRight.normalize();
            this.up = right.cross(forward).normalize();
            this.width = client.getWindow().getGuiScaledWidth();
            this.height = client.getWindow().getGuiScaledHeight();
            double fov = client.options.fov().get();
            this.focalLength = (height * 0.5) / Math.tan(Math.toRadians(fov * 0.5));
        }

        private ScreenPoint project(Vec3 world) {
            Vec3 delta = world.subtract(origin);
            double depth = delta.dot(forward);
            if (depth <= 0.08) return null;
            double screenX = width * 0.5 + (delta.dot(right) / depth) * focalLength;
            double screenY = height * 0.5 - (delta.dot(up) / depth) * focalLength;
            if (!Double.isFinite(screenX) || !Double.isFinite(screenY)) return null;
            if (screenX < -width || screenX > width * 2.0 || screenY < -height || screenY > height * 2.0) return null;
            return new ScreenPoint((int) Math.round(screenX), (int) Math.round(screenY));
        }
    }
}
