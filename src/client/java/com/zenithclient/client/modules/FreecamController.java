package com.zenithclient.client.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/** Independent camera state with the real player held at the activation point. */
public final class FreecamController {
    private static boolean active;
    private static Object playerReference;
    private static double x, y, z;
    private static double previousX, previousY, previousZ;
    private static float yaw, pitch;
    private static double anchorX, anchorY, anchorZ;
    private static float anchorYaw, anchorPitch;

    private FreecamController() { }

    public static void tick(Minecraft mc, boolean enabled, double configuredSpeed) {
        if (!enabled || mc.player == null || mc.level == null) {
            deactivate();
            return;
        }
        if (!active || playerReference != mc.player) activate(mc);

        // Mouse input rotates the player before the client tick. Read that delta,
        // apply it to the detached camera, then restore the real player rotation.
        float observedYaw = mc.player.getYRot();
        float observedPitch = mc.player.getXRot();
        yaw = wrapDegrees(yaw + wrapDegrees(observedYaw - anchorYaw));
        pitch = Math.max(-90.0F, Math.min(90.0F, pitch + observedPitch - anchorPitch));

        boolean forward = mc.options.keyUp.isDown();
        boolean backward = mc.options.keyDown.isDown();
        boolean right = mc.options.keyRight.isDown();
        boolean left = mc.options.keyLeft.isDown();
        boolean up = mc.options.keyJump.isDown();
        boolean down = mc.options.keyShift.isDown();
        boolean sprint = mc.options.keySprint.isDown();

        // Consume movement before LocalPlayer processes it.
        mc.options.keyUp.setDown(false);
        mc.options.keyDown.setDown(false);
        mc.options.keyRight.setDown(false);
        mc.options.keyLeft.setDown(false);
        mc.options.keyJump.setDown(false);
        mc.options.keyShift.setDown(false);

        previousX = x;
        previousY = y;
        previousZ = z;

        double moveForward = (forward ? 1.0 : 0.0) - (backward ? 1.0 : 0.0);
        double moveStrafe = (right ? 1.0 : 0.0) - (left ? 1.0 : 0.0);
        double moveVertical = (up ? 1.0 : 0.0) - (down ? 1.0 : 0.0);
        double length = Math.sqrt(moveForward * moveForward + moveStrafe * moveStrafe
                + moveVertical * moveVertical);
        if (length > 1.0) {
            moveForward /= length;
            moveStrafe /= length;
            moveVertical /= length;
        }

        double speed = Math.max(0.1, Math.min(10.0, configuredSpeed)) * (sprint ? 1.0 : 0.5);
        double radians = Math.toRadians(yaw);
        x += (-Math.sin(radians) * moveForward + Math.cos(radians) * moveStrafe) * speed;
        z += ( Math.cos(radians) * moveForward + Math.sin(radians) * moveStrafe) * speed;
        y += moveVertical * speed;

        mc.player.setPos(anchorX, anchorY, anchorZ);
        mc.player.setYRot(anchorYaw);
        mc.player.setXRot(anchorPitch);
        mc.player.setDeltaMovement(0.0, 0.0, 0.0);
        mc.player.fallDistance = 0.0F;
    }

    private static void activate(Minecraft mc) {
        active = true;
        playerReference = mc.player;
        Vec3 camera = mc.player.getEyePosition();
        x = previousX = camera.x;
        y = previousY = camera.y;
        z = previousZ = camera.z;
        yaw = anchorYaw = mc.player.getYRot();
        pitch = anchorPitch = mc.player.getXRot();
        anchorX = mc.player.getX();
        anchorY = mc.player.getY();
        anchorZ = mc.player.getZ();
    }

    private static void deactivate() {
        active = false;
        playerReference = null;
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

    public static float yaw() { return yaw; }
    public static float pitch() { return pitch; }

    private static float wrapDegrees(float degrees) {
        float wrapped = degrees % 360.0F;
        if (wrapped >= 180.0F) wrapped -= 360.0F;
        if (wrapped < -180.0F) wrapped += 360.0F;
        return wrapped;
    }
}
