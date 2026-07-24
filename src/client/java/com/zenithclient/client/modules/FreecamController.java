package com.zenithclient.client.modules;

import com.zenithclient.client.ZenithClient;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/** Detached camera state. The real player stays at the activation point. */
public final class FreecamController {
    private static boolean active;
    private static Object playerReference;
    private static double x, y, z;
    private static double previousX, previousY, previousZ;
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

        // Mouse input updates the player rotation during the normal client tick.
        // Capture only that delta for the detached camera, then restore the player.
        float observedYaw = mc.player.getYRot();
        float observedPitch = mc.player.getXRot();
        previousYaw = yaw;
        previousPitch = pitch;
        yaw = wrapDegrees(yaw + wrapDegrees(observedYaw - anchorYaw));
        pitch = Math.max(-90.0F, Math.min(90.0F, pitch + observedPitch - anchorPitch));

        double forward = (mc.options.keyUp.isDown() ? 1.0 : 0.0)
                - (mc.options.keyDown.isDown() ? 1.0 : 0.0);
        double strafe = (mc.options.keyRight.isDown() ? 1.0 : 0.0)
                - (mc.options.keyLeft.isDown() ? 1.0 : 0.0);
        double vertical = (mc.options.keyJump.isDown() ? 1.0 : 0.0)
                - (mc.options.keyShift.isDown() ? 1.0 : 0.0);

        double horizontalLength = Math.hypot(forward, strafe);
        if (horizontalLength > 1.0) {
            forward /= horizontalLength;
            strafe /= horizontalLength;
        }

        previousX = x;
        previousY = y;
        previousZ = z;

        double baseSpeed = Math.max(0.1, Math.min(10.0, configuredSpeed));
        double speed = baseSpeed * (mc.options.keySprint.isDown() ? 1.0 : 0.5);
        double radians = Math.toRadians(yaw);
        double sin = Math.sin(radians);
        double cos = Math.cos(radians);

        // Same orientation as vanilla movement: W follows the view and D moves
        // to the camera's right instead of being inverted.
        x += (-sin * forward - cos * strafe) * speed;
        z += ( cos * forward - sin * strafe) * speed;
        y += vertical * speed;

        holdPlayer(mc);
        FreecamVisibility.tick(mc, true, x, z);
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
        yaw = previousYaw = anchorYaw = mc.player.getYRot();
        pitch = previousPitch = anchorPitch = mc.player.getXRot();
        anchorX = mc.player.getX();
        anchorY = mc.player.getY();
        anchorZ = mc.player.getZ();
        holdPlayer(mc);
        ZenithClient.refreshWorldRenderer();
    }

    private static void holdPlayer(Minecraft mc) {
        mc.player.setPos(anchorX, anchorY, anchorZ);
        mc.player.setYRot(anchorYaw);
        mc.player.setXRot(anchorPitch);
        mc.player.setDeltaMovement(0.0, 0.0, 0.0);
        mc.player.fallDistance = 0.0F;
    }

    private static void deactivate() {
        if (!active) return;

        Minecraft mc = Minecraft.getInstance();
        active = false;
        playerReference = null;

        if (previousCameraType != null) {
            mc.options.setCameraType(previousCameraType);
            previousCameraType = null;
        }

        // Rebuild the normal occlusion graph after leaving Freecam.
        if (mc.level != null) {
            FreecamVisibility.tick(mc, false,
                    mc.player == null ? anchorX : mc.player.getX(),
                    mc.player == null ? anchorZ : mc.player.getZ());
            ZenithClient.refreshWorldRenderer();
        }
    }

    public static boolean active() { return active; }

    public static Vec3 position(float partialTick) {
        double t = Math.max(0.0, Math.min(1.0, partialTick));
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
