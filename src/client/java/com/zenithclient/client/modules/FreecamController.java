package com.zenithclient.client.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/** Independent camera position and rotation state used by the Freecam mixins. */
public final class FreecamController {
    private static boolean active;
    private static double x, y, z;
    private static float yaw, pitch;
    private static double anchorX, anchorY, anchorZ;
    private static float anchorYaw, anchorPitch;
    private static double velocityX, velocityY, velocityZ;

    private FreecamController() { }

    public static void tick(Minecraft mc, boolean enabled, double configuredSpeed) {
        if (!enabled || mc.player == null || mc.level == null) {
            if (active) deactivate();
            return;
        }
        if (!active) activate(mc);

        float observedYaw = mc.player.getYRot();
        float observedPitch = mc.player.getXRot();
        float deltaYaw = wrapDegrees(observedYaw - anchorYaw);
        float deltaPitch = observedPitch - anchorPitch;
        if (Math.abs(deltaYaw) > 0.0001F || Math.abs(deltaPitch) > 0.0001F) {
            look(deltaYaw, deltaPitch);
        }

        double forward = (mc.options.keyUp.isDown() ? 1.0 : 0.0)
                - (mc.options.keyDown.isDown() ? 1.0 : 0.0);
        double strafe = (mc.options.keyRight.isDown() ? 1.0 : 0.0)
                - (mc.options.keyLeft.isDown() ? 1.0 : 0.0);
        double vertical = (mc.options.keyJump.isDown() ? 1.0 : 0.0)
                - (mc.options.keyShift.isDown() ? 1.0 : 0.0);
        double speed = Math.max(0.1, Math.min(10.0, configuredSpeed)) * 0.35;
        if (mc.options.keySprint.isDown()) speed *= 2.5;
        move(forward, strafe, vertical, speed);

        // Keep the real player at the activation anchor while only the camera moves.
        mc.player.setPos(anchorX, anchorY, anchorZ);
        mc.player.setYRot(anchorYaw);
        mc.player.setXRot(anchorPitch);
        mc.player.setDeltaMovement(0.0, 0.0, 0.0);
        mc.player.fallDistance = 0.0F;
    }

    private static void activate(Minecraft mc) {
        active = true;
        Vec3 camera = mc.player.getEyePosition();
        x = camera.x;
        y = camera.y;
        z = camera.z;
        yaw = mc.player.getYRot();
        pitch = mc.player.getXRot();
        anchorX = mc.player.getX();
        anchorY = mc.player.getY();
        anchorZ = mc.player.getZ();
        anchorYaw = yaw;
        anchorPitch = pitch;
        velocityX = velocityY = velocityZ = 0.0;
    }

    private static void deactivate() {
        active = false;
        velocityX = velocityY = velocityZ = 0.0;
    }

    public static boolean active() { return active; }
    public static Vec3 position() { return new Vec3(x, y, z); }
    public static float yaw() { return yaw; }
    public static float pitch() { return pitch; }

    private static void look(float deltaYaw, float deltaPitch) {
        yaw = wrapDegrees(yaw + deltaYaw);
        pitch = Math.max(-90.0F, Math.min(90.0F, pitch + deltaPitch));
    }

    private static void move(double forward, double strafe, double vertical, double speed) {
        double length = Math.sqrt(forward * forward + strafe * strafe + vertical * vertical);
        if (length > 1.0) {
            forward /= length;
            strafe /= length;
            vertical /= length;
        }

        double r = Math.toRadians(yaw);
        double wantedX = (-Math.sin(r) * forward + Math.cos(r) * strafe) * speed;
        double wantedZ = (Math.cos(r) * forward + Math.sin(r) * strafe) * speed;
        double wantedY = vertical * speed;
        double easing = Math.abs(forward) + Math.abs(strafe) + Math.abs(vertical) > 0.001
                ? 0.48 : 0.30;

        velocityX += (wantedX - velocityX) * easing;
        velocityY += (wantedY - velocityY) * easing;
        velocityZ += (wantedZ - velocityZ) * easing;
        if (Math.abs(velocityX) < 0.0001) velocityX = 0.0;
        if (Math.abs(velocityY) < 0.0001) velocityY = 0.0;
        if (Math.abs(velocityZ) < 0.0001) velocityZ = 0.0;

        x += velocityX;
        y += velocityY;
        z += velocityZ;
    }

    private static float wrapDegrees(float degrees) {
        float wrapped = degrees % 360.0F;
        if (wrapped >= 180.0F) wrapped -= 360.0F;
        if (wrapped < -180.0F) wrapped += 360.0F;
        return wrapped;
    }
}
