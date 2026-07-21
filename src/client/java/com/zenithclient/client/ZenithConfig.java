package com.zenithclient.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ZenithConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("zenithclient.json");

    public EntityHighlightMode entityHighlightMode = EntityHighlightMode.ALL_MOBS;
    public BlockHighlightMode blockHighlightMode = BlockHighlightMode.VALUABLE_ORES;

    public boolean entityHighlights = true;
    public boolean playerEsp = true;
    public boolean entityOutline = true;
    public boolean entityFill = true;
    public boolean playerTracers = false;
    public boolean playerNameTags = true;
    public boolean entityTracers = false;
    public boolean entityNameTags = false;
    public boolean blockHighlights = false;
    public boolean trajectoryPreview = false;
    public boolean showFps = true;
    public boolean showCoordinates = true;
    public boolean fullbright = false;
    public boolean autoSprint = false;
    public boolean flight = false;
    public boolean xray = false;
    public boolean noSlow = false;
    public boolean noStun = false;
    public boolean noFall = false;
    public boolean criticals = false;
    public boolean airJump = false;
    public int xrayOpacity = 0;
    public XrayMode xrayMode = XrayMode.ORES;
    public boolean chatToggleMessages = true;

    public int blockRadius = 12;
    public int entityRange = 96;
    public int lineDensity = 5;
    public int entityOutlineColor = 0xFF3B30;
    public int playerOutlineColor = 0x00E5FF;
    public int playerFillColor = 0x00E5FF;
    public int playerFillOpacity = 14;
    public int playerOutlineThickness = 2;
    public int entityFillColor = 0xFF3B30;
    public int entityFillOpacity = 18;
    public int entityOutlineThickness = 2;
    public EspShape playerEspShape = EspShape.BOX_3D;
    public EspShape entityEspShape = EspShape.BOX_3D;
    public int blockOutlineColor = 0xFFD166;
    public int blockFillColor = 0xFFD166;
    public int blockFillOpacity = 10;
    public int trajectoryColor = 0xFFFF6B35;
    public int trajectoryThickness = 1;
    public double trajectoryStartDistance = 1.75;

    // Smooth client-side movement. Servers may correct movement unless they permit it.
    public double flightSpeed = 1.0;
    public double flightVerticalSpeed = 1.0;
    public double flightSprintMultiplier = 2.0;

    // Each module has its own optional toggle key. -1 means unbound.
    public int entityHighlightsKey = GLFW.GLFW_KEY_H;
    public int playerEspKey = GLFW.GLFW_KEY_P;
    public int blockHighlightsKey = GLFW.GLFW_KEY_J;
    public int trajectoryPreviewKey = GLFW.GLFW_KEY_K;
    public int flightKey = GLFW.GLFW_KEY_F;
    public int autoSprintKey = -1;
    public int fullbrightKey = GLFW.GLFW_KEY_G;
    public int showFpsKey = -1;
    public int showCoordinatesKey = -1;
    public int xrayKey = GLFW.GLFW_KEY_Z;
    public int noSlowKey = -1;
    public int noStunKey = -1;
    public int noFallKey = -1;
    public int criticalsKey = -1;
    public int airJumpKey = -1;

    // Comma-separated entity type filters. Empty means use the selected category.
    public String entitySearch = "";

    public int uiAccentColor = 0xFFFF6B35;
    public int uiPanelColor = 0xFF151515;
    public int uiSidebarColor = 0xFF1D1D1D;
    public int uiPanelOpacity = 94;
    public int uiButtonOpacity = 88;
    public int lastUiTab = 0;

    public static ZenithConfig load() {
        if (!Files.exists(CONFIG_PATH)) return new ZenithConfig();
        try {
            ZenithConfig config = GSON.fromJson(Files.readString(CONFIG_PATH), ZenithConfig.class);
            if (config == null) return new ZenithConfig();
            config.sanitize();
            return config;
        } catch (Exception ignored) {
            return new ZenithConfig();
        }
    }

    private void sanitize() {
        flightSpeed = clamp(flightSpeed, 0.1, 10.0, 1.0);
        flightVerticalSpeed = clamp(flightVerticalSpeed, 0.1, 10.0, 1.0);
        flightSprintMultiplier = clamp(flightSprintMultiplier, 1.0, 4.0, 2.0);
        xrayOpacity = (int) clamp(xrayOpacity, 0, 255, 0);
        blockRadius = (int) clamp(blockRadius, 4, 32, 12);
        entityRange = (int) clamp(entityRange, 8, 256, 96);
        playerFillOpacity = (int) clamp(playerFillOpacity, 0, 100, 14);
        entityFillOpacity = (int) clamp(entityFillOpacity, 0, 100, 18);
        blockFillOpacity = (int) clamp(blockFillOpacity, 0, 100, 10);
        if (playerEspShape == null) playerEspShape = EspShape.BOX_3D;
        if (entityEspShape == null) entityEspShape = EspShape.BOX_3D;
        if (xrayMode == null) xrayMode = XrayMode.ORES;
    }

    private static double clamp(double value, double min, double max, double fallback) {
        if (!Double.isFinite(value) || value < min || value > max) return fallback;
        return value;
    }

    public void save() {
        sanitize();
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, GSON.toJson(this));
        } catch (IOException exception) {
            System.err.println("ZenithClient could not save config: " + exception.getMessage());
        }
    }

    public enum EntityHighlightMode {
        PLAYERS("Players"), HOSTILE_MOBS("Hostile mobs"), PASSIVE_MOBS("Passive mobs"),
        ZOMBIES("Zombies"), CREEPERS("Creepers"), SKELETONS("Skeletons"),
        ALL_MOBS("All mobs"), ALL_ENTITIES("All entities");

        private final String displayName;
        EntityHighlightMode(String displayName) { this.displayName = displayName; }
        public String displayName() { return displayName; }
        public EntityHighlightMode next() {
            EntityHighlightMode[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }

    public enum BlockHighlightMode {
        VALUABLE_ORES("Valuable ores"), ALL_ORES("All ores"), CONTAINERS("Containers"),
        SPAWNERS("Spawners"), ANCIENT_DEBRIS("Ancient debris"), DIAMOND_ORE("Diamond ore");

        private final String displayName;
        BlockHighlightMode(String displayName) { this.displayName = displayName; }
        public String displayName() { return displayName; }
        public BlockHighlightMode next() {
            BlockHighlightMode[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }

    public enum EspShape {
        BOX_3D("3D box"), BOX_2D("2D box"), CORNERS("Corners");

        private final String displayName;
        EspShape(String displayName) { this.displayName = displayName; }
        public String displayName() { return displayName; }
        public EspShape next() {
            EspShape[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }

    public enum XrayMode {
        ORES("Ores"), VALUABLE_ORES("Valuable ores"), DIAMOND_DEBRIS("Diamond + debris");

        private final String displayName;
        XrayMode(String displayName) { this.displayName = displayName; }
        public String displayName() { return displayName; }
        public XrayMode next() {
            XrayMode[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }
}
