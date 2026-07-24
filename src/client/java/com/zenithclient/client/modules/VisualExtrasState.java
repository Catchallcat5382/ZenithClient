package com.zenithclient.client.modules;

/**
 * Compatibility source retained after removing the experimental visual extras.
 * All methods are intentionally inactive.
 */
public final class VisualExtrasState {
    private VisualExtrasState() { }

    public static boolean zoom() { return false; }
    public static boolean clearWeather() { return false; }
    public static boolean daylight() { return false; }
    public static boolean noHurtCamera() { return false; }
    public static boolean noPortalOverlay() { return false; }

    public static void toggleZoom() { }
    public static void toggleClearWeather() { }
    public static void toggleDaylight() { }
    public static void toggleNoHurtCamera() { }
    public static void toggleNoPortalOverlay() { }
}
