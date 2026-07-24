package com.zenithclient.client.modules;

/**
 * Compatibility source retained so existing project copies do not require a
 * manual file deletion. Standalone Breach Swap is no longer active.
 */
public final class BreachSwapController {
    private BreachSwapController() { }

    public static boolean isSwapActive() { return false; }
    public static void reset() { }
    public static void tick() { }
}
