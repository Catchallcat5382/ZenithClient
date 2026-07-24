package com.zenithclient.client.modules;

import com.zenithclient.client.ZenithClient;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/** Detached spectator-style camera state. */
public final class FreecamController {
    private static boolean active;
    private static Object playerReference;
    private static double x, y, z;
    private static double previousX, previousY, previousZ;
    private static double velocityX, velocityY, velocityZ;
    private static float yaw, pitch;
    private static float previousYaw, previousPitch;
    private static double anchorX, anchorY, anchorZ;
    private static float anchorYaw, anchorPitch;
    private static CameraType previousCameraType;

    private FreecamController() { }

    public static void tick(Minecraft mc, boolean enabled, double configuredSpeed) {
        if (!enabled || mc.player == null || mc.level == null) {
            deactivate();
            return;
        }

        if (!active || playerReference != mc.player) activate(mc);
        if (mc.options.getCameraType() != CameraType.FIRST_PERSON) {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
        }

        captureLook(mc);

        double forward = (mc.options.keyUp.isDown() ? 1.0D : 0.0D)
                - (mc.options.keyDown.isDown() ? 1.0D : 0.0D);
        double strafe = (mc.options.keyRight.isDown() ? 1.0D : 0.0D)
                - (mc.options.keyLeft.isDown() ? 1.0D : 0.0D);
        double vertical = (mc.options.keyJump.isDown() ? 1.0D : 0.0D)
                - (mc.options.keyShift.isDown() ? 1.0D : 0.0D);

        double horizontalLength = Math.hypot(forward, strafe);
        if (horizontalLength > 1.0D) {
            forward /= horizontalLength;
            strafe /= horizontalLength;
        }

        double baseSpeed = Math.max(0.1D, Math.min(10.0D, configuredSpeed));
        double targetSpeed = baseSpeed
                * (mc.options.keySprint.isDown() ? 0.95D : 0.45D);

        double radians = Math.toRadians(yaw);
        double sin = Math.sin(radians);
        double cos = Math.cos(radians);

        double wantedX = (-sin * forward - cos * strafe) * targetSpeed;
        double wantedZ = ( cos * forward - sin * strafe) * targetSpeed;
        double wantedY = vertical * targetSpeed;

        double acceleration = 0.48D;
        velocityX += (wantedX - velocityX) * acceleration;
        velocityY += (wantedY - velocityY) * acceleration;
        velocityZ += (wantedZ - velocityZ) * acceleration;

        if (forward == 0.0D && strafe == 0.0D) {
            velocityX *= 0.58D;
            velocityZ *= 0.58D;
        }
        if (vertical == 0.0D) velocityY *= 0.58D;

        if (Math.abs(velocityX) < 0.0001D) velocityX = 0.0D;
        if (Math.abs(velocityY) < 0.0001D) velocityY = 0.0D;
        if (Math.abs(velocityZ) < 0.0001D) velocityZ = 0.0D;

        previousX = x;
        previousY = y;
        previousZ = z;

        x += velocityX;
        y += velocityY;
        z += velocityZ;

        holdPlayer(mc);
        FreecamVisibility.tick(mc, true, x, z);
    }

    private static void captureLook(Minecraft mc) {
        float observedYaw = mc.player.getYRot();
        float observedPitch = mc.player.getXRot();
        float deltaYaw = wrapDegrees(observedYaw - anchorYaw);
        float deltaPitch = observedPitch - anchorPitch;

        previousYaw = yaw;
        previousPitch = pitch;
        yaw = wrapDegrees(yaw + deltaYaw);
        pitch = Math.max(-90.0F, Math.min(90.0F, pitch + deltaPitch));
    }

    private static void activate(Minecraft mc) {
        active = true;
        playerReference = mc.player;
        previousCameraType = mc.options.getCameraType();
        mc.options.setCameraType(CameraType.FIRST_PERSON);

        Vec3 camera = mc.player.getEyePosition();
        x = previousX = camera.x;
        y = previousY = camera.y;
        z = previousZ = camera.z;
        velocityX = velocityY = velocityZ = 0.0D;

        yaw = previousYaw = anchorYaw = mc.player.getYRot();
        pitch = previousPitch = anchorPitch = mc.player.getXRot();

        anchorX = mc.player.getX();
        anchorY = mc.player.getY();
        anchorZ = mc.player.getZ();

        holdPlayer(mc);
        ZenithClient.refreshWorldRenderer();
        FreecamVisibility.resetForActivation(mc, x, z);
    }

    private static void holdPlayer(Minecraft mc) {
        mc.player.setPos(anchorX, anchorY, anchorZ);
        mc.player.setYRot(anchorYaw);
        mc.player.setXRot(anchorPitch);
        mc.player.setDeltaMovement(0.0D, 0.0D, 0.0D);
        mc.player.fallDistance = 0.0F;
    }

    private static void deactivate() {
        if (!active) return;

        Minecraft mc = Minecraft.getInstance();
        active = false;
        playerReference = null;
        velocityX = velocityY = velocityZ = 0.0D;

        if (previousCameraType != null) {
            mc.options.setCameraType(previousCameraType);
            previousCameraType = null;
        }

        FreecamVisibility.resetAfterDeactivation(mc);
        if (mc.level != null) ZenithClient.refreshWorldRenderer();
    }

    public static boolean active() {
        return active;
    }

    public static Vec3 position(float partialTick) {
        double t = Math.max(0.0D, Math.min(1.0D, partialTick));
        return new Vec3(
                previousX + (x - previousX) * t,
                previousY + (y - previousY) * t,
                previousZ + (z - previousZ) * t
        );
    }

    public static float yaw(float partialTick) {
        float t = Math.max(0.0F, Math.min(1.0F, partialTick));
        return previousYaw + wrapDegrees(yaw - previousYaw) * t;
    }

    public static float pitch(float partialTick) {
        float t = Math.max(0.0F, Math.min(1.0F, partialTick));
        return previousPitch + (pitch - previousPitch) * t;
    }

    private static float wrapDegrees(float degrees) {
        float wrapped = degrees % 360.0F;
        if (wrapped >= 180.0F) wrapped -= 360.0F;
        if (wrapped < -180.0F) wrapped += 360.0F;
        return wrapped;
    }
}
