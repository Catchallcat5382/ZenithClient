package com.zenithclient.client.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/** Camera-state controller used by ZenithClient's camera/input mixins. */
public final class FreecamController {
    private static boolean active;
    private static double x, y, z;
    private static float yaw, pitch;
    private static double velocityX, velocityY, velocityZ;

    private FreecamController() {}

    public static void setActive(Minecraft mc, boolean value) {
        if (value == active || mc.player == null) return;
        active = value;
        if (value) {
            Vec3 pos = mc.player.getEyePosition();
            x = pos.x;
            y = pos.y;
            z = pos.z;
            yaw = mc.player.getYRot();
            pitch = mc.player.getXRot();
            velocityX = velocityY = velocityZ = 0.0;
        } else {
            velocityX = velocityY = velocityZ = 0.0;
        }
    }

    public static boolean active() { return active; }
    public static double x() { return x; }
    public static double y() { return y; }
    public static double z() { return z; }
    public static float yaw() { return yaw; }
    public static float pitch() { return pitch; }

    public static void look(double dx, double dy) {
        yaw += (float) dx;
        pitch = Math.max(-90.0F, Math.min(90.0F, pitch + (float) dy));
    }

    public static void move(double forward, double strafe, double vertical, double speed) {
        double length = Math.sqrt(forward * forward + strafe * strafe + vertical * vertical);
        if (length > 1.0) { forward /= length; strafe /= length; vertical /= length; }

        double r = Math.toRadians(yaw);
        double wantedX = (-Math.sin(r) * forward + Math.cos(r) * strafe) * speed;
        double wantedZ = ( Math.cos(r) * forward + Math.sin(r) * strafe) * speed;
        double wantedY = vertical * speed;

        double easing = Math.abs(forward) + Math.abs(strafe) + Math.abs(vertical) > 0.001 ? 0.45 : 0.25;
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
}
