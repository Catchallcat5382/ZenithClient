package com.zenithclient.client;

import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/** A completely custom-drawn Click GUI. No vanilla Button widgets are used. */
public final class ZenithScreen extends Screen {
    private static final Identifier LOGO = Identifier.fromNamespaceAndPath(ZenithClient.MOD_ID, "icon.png");

    private enum Category { VISUALS, COMBAT, MOVEMENT, HUD, CONFIG }
    private enum Module { PLAYER_ESP, ENTITY_OUTLINES, ITEM_ESP, PROJECTILE_ESP, BLOCK_OUTLINES, BOW_TRAJECTORY, XRAY, NO_BLINDNESS, NO_FIRE_OVERLAY, FLIGHT, SPEED, AUTO_SPRINT, NO_SLOW, NO_STUN, NO_FALL, AIR_JUMP, FREECAM, CRITICALS, AUTO_TOTEM, ATTRIBUTE_SWAP, KILL_AURA, REACH, INFINITE_REACH, MACE_KILL, SUPER_PUNCH, FULLBRIGHT, FPS, COORDINATES }
    private enum HitType { TAB, MODULE, DONE, THEME_ACCENT, PANEL_OPACITY, BUTTON_OPACITY, CHAT_MESSAGES, RESET_THEME }

    private record Hitbox(HitType type, int value, int x, int y, int width, int height) {
        boolean contains(double mx, double my) { return mx >= x && mx < x + width && my >= y && my < y + height; }
    }

    private final Screen parent;
    private final ZenithConfig config;
    private final List<Hitbox> hitboxes = new ArrayList<>();
    private Category selectedCategory;

    public ZenithScreen(Screen parent, ZenithConfig config) {
        super(Component.literal("ZenithClient"));
        this.parent = parent;
        this.config = config;
        Category[] tabs = Category.values();
        selectedCategory = tabs[Math.max(0, Math.min(tabs.length - 1, config.lastUiTab))];
    }

    @Override
    protected void init() {
        hitboxes.clear();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        int pw = Math.min(680, Math.max(500, width - 50));
        int ph = Math.min(390, Math.max(300, height - 50));
        int left = (width - pw) / 2;
        int top = (height - ph) / 2;
        int sidebar = 134;
        int accent = opaque(config.uiAccentColor);
        int panel = alpha(config.uiPanelColor, config.uiPanelOpacity);
        int side = alpha(config.uiSidebarColor, config.uiPanelOpacity);

        hitboxes.clear();
        g.fill(0, 0, width, height, 0x76000000);
        g.fill(left, top, left + pw, top + ph, panel);
        g.fill(left, top, left + sidebar, top + ph, side);
        g.fill(left, top, left + pw, top + 3, accent);

        // Actual Mod Menu icon.
        g.blit(RenderPipelines.GUI_TEXTURED, LOGO, left + 8, top + 6, 0, 0, 48, 48, 256, 256, 256, 256, 0xFFFFFFFF);
        g.text(font, "ZenithClient", left + 61, top + 17, 0xFFF4F4F4, true);
        g.text(font, ZenithClient.versionLabel(), left + 61, top + 29, 0xFF9A9A9A, false);

        int ty = top + 58;
        for (Category c : Category.values()) {
            boolean active = c == selectedCategory;
            boolean hover = inside(mouseX, mouseY, left + 10, ty, sidebar - 20, 27);
            int bg = active ? alpha(accent, 78) : hover ? 0x3AFFFFFF : 0x16000000;
            customButton(g, left + 10, ty, sidebar - 20, 27, label(c), bg, active ? accent : 0xFFD0D0D0, active);
            hitboxes.add(new Hitbox(HitType.TAB, c.ordinal(), left + 10, ty, sidebar - 20, 27));
            ty += 34;
        }

        int cx = left + sidebar + 18;
        int cy = top + 56;
        int cw = pw - sidebar - 34;
        g.text(font, label(selectedCategory), cx, top + 22, 0xFFF1F1F1, true);
        g.fill(cx, top + 40, left + pw - 16, top + 41, 0x34FFFFFF);

        if (selectedCategory == Category.CONFIG) {
            drawConfig(g, mouseX, mouseY, cx, cy, cw, accent);
        } else {
            List<Module> modules = modules(selectedCategory);
            int gap = 10;
            int bw = (cw - gap) / 2;
            int row = 0;
            for (int i = 0; i < modules.size(); i++) {
                Module m = modules.get(i);
                int x = cx + (i % 2) * (bw + gap);
                int y = cy + (i / 2) * 48;
                drawModule(g, mouseX, mouseY, m, x, y, bw, 38, accent);
                row = Math.max(row, i / 2);
            }
        }

        int dx = left + pw - 90;
        int dy = top + ph - 31;
        boolean hoverDone = inside(mouseX, mouseY, dx, dy, 74, 20);
        customButton(g, dx, dy, 74, 20, "Done", hoverDone ? alpha(accent, 150) : alpha(accent, 100), 0xFFFFFFFF, true);
        hitboxes.add(new Hitbox(HitType.DONE, 0, dx, dy, 74, 20));

        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    private void drawModule(GuiGraphicsExtractor g, int mx, int my, Module m, int x, int y, int w, int h, int accent) {
        boolean enabled = enabled(m);
        boolean hover = inside(mx, my, x, y, w, h);
        int bg = enabled ? alpha(accent, Math.min(96, config.uiButtonOpacity)) : hover ? 0x4A333333 : alpha(0xFF222222, config.uiButtonOpacity);
        g.fill(x, y, x + w, y + h, bg);
        g.fill(x, y, x + 3, y + h, enabled ? accent : 0xFF555555);
        if (hover) {
            g.fill(x, y, x + w, y + 1, 0x66FFFFFF);
            g.fill(x, y + h - 1, x + w, y + h, 0x33000000);
        }
        g.text(font, moduleName(m), x + 12, y + 9, enabled ? 0xFFFFFFFF : 0xFFD4D4D4, true);
        g.text(font, enabled ? "ENABLED" : "DISABLED", x + 12, y + 23, enabled ? 0xFFBFFFC9 : 0xFF888888, false);
        int sx = x + w - 32;
        int sy = y + 11;
        int track = enabled ? 0xFF1F1F1F : 0xFF555555;
        g.fill(sx, sy, sx + 22, sy + 12, track);
        g.fill(sx + 1, sy + 1, sx + 21, sy + 11, enabled ? accent : 0xFF6A6A6A);
        int knobX = enabled ? sx + 12 : sx + 2;
        g.fill(knobX, sy + 2, knobX + 8, sy + 10, 0xFFFFFFFF);
        g.fill(knobX + 1, sy + 3, knobX + 7, sy + 9, enabled ? 0xFFEFEFEF : 0xFFCFCFCF);
        hitboxes.add(new Hitbox(HitType.MODULE, m.ordinal(), x, y, w, h));
    }

    private void drawConfig(GuiGraphicsExtractor g, int mx, int my, int x, int y, int w, int accent) {
        int gap = 10;
        int bw = (w - gap) / 2;
        configButton(g, mx, my, HitType.THEME_ACCENT, x, y, bw, "Accent", colorName(config.uiAccentColor), accent);
        configButton(g, mx, my, HitType.PANEL_OPACITY, x + bw + gap, y, bw, "Panel opacity", config.uiPanelOpacity + "%", accent);
        configButton(g, mx, my, HitType.BUTTON_OPACITY, x, y + 48, bw, "Button opacity", config.uiButtonOpacity + "%", accent);
        configButton(g, mx, my, HitType.CHAT_MESSAGES, x + bw + gap, y + 48, bw, "Chat messages", config.chatToggleMessages ? "ON" : "OFF", accent);
        configButton(g, mx, my, HitType.RESET_THEME, x, y + 96, bw, "Theme", "Reset", accent);
    }

    private void configButton(GuiGraphicsExtractor g, int mx, int my, HitType type, int x, int y, int w, String title, String value, int accent) {
        boolean hover = inside(mx, my, x, y, w, 38);
        g.fill(x, y, x + w, y + 38, hover ? 0x4A363636 : 0xCC222222);
        g.fill(x, y, x + 3, y + 38, accent);
        g.text(font, title, x + 12, y + 8, 0xFFF0F0F0, true);
        g.text(font, value, x + 12, y + 23, accent, false);
        hitboxes.add(new Hitbox(type, 0, x, y, w, 38));
    }

    private void customButton(GuiGraphicsExtractor g, int x, int y, int w, int h, String text, int bg, int fg, boolean shadow) {
        g.fill(x, y, x + w, y + h, bg);
        int tw = font.width(text);
        g.text(font, text, x + (w - tw) / 2, y + (h - 8) / 2, fg, shadow);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        for (Hitbox h : hitboxes) {
            if (!h.contains(event.x(), event.y())) continue;
            if (h.type == HitType.MODULE) {
                Module module = Module.values()[h.value];
                if (event.buttonInfo().button() == 1) openSettings(module);
                else if (event.buttonInfo().button() == 0) toggle(module);
                return true;
            }
            if (event.buttonInfo().button() != 0) return true;
            switch (h.type) {
                case TAB -> { selectedCategory = Category.values()[h.value]; config.lastUiTab = h.value; }
                case DONE -> { onClose(); return true; }
                case THEME_ACCENT -> config.uiAccentColor = nextThemeColor(config.uiAccentColor);
                case PANEL_OPACITY -> { config.uiPanelOpacity += 5; if (config.uiPanelOpacity > 100) config.uiPanelOpacity = 70; }
                case BUTTON_OPACITY -> { config.uiButtonOpacity += 5; if (config.uiButtonOpacity > 100) config.uiButtonOpacity = 55; }
                case CHAT_MESSAGES -> config.chatToggleMessages = !config.chatToggleMessages;
                case RESET_THEME -> { config.uiAccentColor = 0xFFFF6B35; config.uiPanelColor = 0xFF151515; config.uiSidebarColor = 0xFF1D1D1D; config.uiPanelOpacity = 94; config.uiButtonOpacity = 88; }
                default -> { }
            }
            config.save();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    private void openSettings(Module m) {
        ModuleSettingsScreen.Type type = switch (m) {
            case PLAYER_ESP -> ModuleSettingsScreen.Type.PLAYER;
            case ENTITY_OUTLINES -> ModuleSettingsScreen.Type.ENTITY;
            case ITEM_ESP -> ModuleSettingsScreen.Type.ITEM;
            case PROJECTILE_ESP -> ModuleSettingsScreen.Type.PROJECTILE;
            case BLOCK_OUTLINES -> ModuleSettingsScreen.Type.BLOCKS;
            case BOW_TRAJECTORY -> ModuleSettingsScreen.Type.TRAJECTORY;
            case XRAY -> ModuleSettingsScreen.Type.XRAY;
            case NO_BLINDNESS -> ModuleSettingsScreen.Type.NO_BLINDNESS;
            case NO_FIRE_OVERLAY -> ModuleSettingsScreen.Type.NO_FIRE_OVERLAY;
            case FLIGHT -> ModuleSettingsScreen.Type.FLIGHT;
            case AUTO_SPRINT -> ModuleSettingsScreen.Type.AUTO_SPRINT;
            case NO_SLOW -> ModuleSettingsScreen.Type.NO_SLOW;
            case NO_STUN -> ModuleSettingsScreen.Type.NO_STUN;
            case NO_FALL -> ModuleSettingsScreen.Type.NO_FALL;
            case CRITICALS -> ModuleSettingsScreen.Type.CRITICALS;
            case AUTO_TOTEM -> ModuleSettingsScreen.Type.AUTO_TOTEM;
            case ATTRIBUTE_SWAP -> ModuleSettingsScreen.Type.ATTRIBUTE_SWAP;
            case KILL_AURA -> ModuleSettingsScreen.Type.KILL_AURA;
            case REACH -> ModuleSettingsScreen.Type.REACH;
            case INFINITE_REACH -> ModuleSettingsScreen.Type.INFINITE_REACH;
            case SPEED -> ModuleSettingsScreen.Type.SPEED;
            case MACE_KILL -> ModuleSettingsScreen.Type.MACE_KILL;
            case SUPER_PUNCH -> ModuleSettingsScreen.Type.SUPER_PUNCH;
            case AIR_JUMP -> ModuleSettingsScreen.Type.AIR_JUMP;
            case FREECAM -> ModuleSettingsScreen.Type.FREECAM;
            case FULLBRIGHT -> ModuleSettingsScreen.Type.FULLBRIGHT;
            case FPS -> ModuleSettingsScreen.Type.FPS;
            case COORDINATES -> ModuleSettingsScreen.Type.COORDINATES;
        };
        if (minecraft != null) minecraft.setScreenAndShow(ModuleSettingsScreen.of(this, config, type));
    }

    private void toggle(Module m) {
        boolean beforeFlight = config.flight;
        switch (m) {
            case PLAYER_ESP -> config.playerEsp = !config.playerEsp;
            case ENTITY_OUTLINES -> config.entityHighlights = !config.entityHighlights;
            case ITEM_ESP -> config.itemEsp = !config.itemEsp;
            case PROJECTILE_ESP -> config.projectileEsp = !config.projectileEsp;
            case BLOCK_OUTLINES -> config.blockHighlights = !config.blockHighlights;
            case BOW_TRAJECTORY -> config.trajectoryPreview = !config.trajectoryPreview;
            case XRAY -> config.xray = !config.xray;
            case NO_BLINDNESS -> config.noBlindness = !config.noBlindness;
            case NO_FIRE_OVERLAY -> config.noFireOverlay = !config.noFireOverlay;
            case FLIGHT -> config.flight = !config.flight;
            case AUTO_SPRINT -> config.autoSprint = !config.autoSprint;
            case NO_SLOW -> config.noSlow = !config.noSlow;
            case NO_STUN -> config.noStun = !config.noStun;
            case NO_FALL -> config.noFall = !config.noFall;
            case CRITICALS -> config.criticals = !config.criticals;
            case AUTO_TOTEM -> config.autoTotem = !config.autoTotem;
            case ATTRIBUTE_SWAP -> config.attributeSwap = !config.attributeSwap;
            case KILL_AURA -> config.killAura = !config.killAura;
            case REACH -> config.reach = !config.reach;
            case INFINITE_REACH -> config.infiniteReach = !config.infiniteReach;
            case SPEED -> config.speed = !config.speed;
            case MACE_KILL -> config.maceKill = !config.maceKill;
            case SUPER_PUNCH -> config.superPunch = !config.superPunch;
            case AIR_JUMP -> config.airJump = !config.airJump;
            case FREECAM -> config.freecam = !config.freecam;
            case FULLBRIGHT -> config.fullbright = !config.fullbright;
            case FPS -> config.showFps = !config.showFps;
            case COORDINATES -> config.showCoordinates = !config.showCoordinates;
        }
        config.save();
        if (beforeFlight && !config.flight) ZenithClient.stopFlightMotion();
        if (m == Module.XRAY) ZenithClient.refreshWorldRenderer();
    }

    private boolean enabled(Module m) {
        return switch (m) {
            case PLAYER_ESP -> config.playerEsp;
            case ENTITY_OUTLINES -> config.entityHighlights;
            case ITEM_ESP -> config.itemEsp;
            case PROJECTILE_ESP -> config.projectileEsp;
            case BLOCK_OUTLINES -> config.blockHighlights;
            case BOW_TRAJECTORY -> config.trajectoryPreview;
            case XRAY -> config.xray;
            case NO_BLINDNESS -> config.noBlindness;
            case NO_FIRE_OVERLAY -> config.noFireOverlay;
            case FLIGHT -> config.flight;
            case AUTO_SPRINT -> config.autoSprint;
            case NO_SLOW -> config.noSlow;
            case NO_STUN -> config.noStun;
            case NO_FALL -> config.noFall;
            case CRITICALS -> config.criticals;
            case AUTO_TOTEM -> config.autoTotem;
            case ATTRIBUTE_SWAP -> config.attributeSwap;
            case KILL_AURA -> config.killAura;
            case REACH -> config.reach;
            case INFINITE_REACH -> config.infiniteReach;
            case SPEED -> config.speed;
            case MACE_KILL -> config.maceKill;
            case SUPER_PUNCH -> config.superPunch;
            case AIR_JUMP -> config.airJump;
            case FREECAM -> config.freecam;
            case FULLBRIGHT -> config.fullbright;
            case FPS -> config.showFps;
            case COORDINATES -> config.showCoordinates;
        };
    }

    private static List<Module> modules(Category c) {
        return switch (c) {
            case VISUALS -> List.of(Module.PLAYER_ESP, Module.ENTITY_OUTLINES, Module.ITEM_ESP, Module.PROJECTILE_ESP, Module.BLOCK_OUTLINES, Module.BOW_TRAJECTORY, Module.XRAY, Module.NO_BLINDNESS, Module.NO_FIRE_OVERLAY);
            case COMBAT -> List.of(Module.CRITICALS, Module.AUTO_TOTEM, Module.ATTRIBUTE_SWAP, Module.KILL_AURA, Module.REACH, Module.INFINITE_REACH, Module.MACE_KILL, Module.SUPER_PUNCH);
            case MOVEMENT -> List.of(Module.FLIGHT, Module.SPEED, Module.AUTO_SPRINT, Module.NO_SLOW, Module.NO_STUN, Module.NO_FALL, Module.AIR_JUMP, Module.FREECAM);
            case HUD -> List.of(Module.FULLBRIGHT, Module.FPS, Module.COORDINATES);
            case CONFIG -> List.of();
        };
    }

    private static String moduleName(Module m) { return switch (m) {
        case PLAYER_ESP -> "Player ESP";
            case ENTITY_OUTLINES -> "Entity ESP"; case ITEM_ESP -> "Item ESP"; case PROJECTILE_ESP -> "Projectile ESP"; case BLOCK_OUTLINES -> "Block ESP"; case BOW_TRAJECTORY -> "Trajectories";
        case XRAY -> "X-Ray"; case NO_BLINDNESS -> "No Blindness"; case NO_FIRE_OVERLAY -> "No Fire Overlay"; case FLIGHT -> "Flight"; case AUTO_SPRINT -> "Auto Sprint"; case NO_SLOW -> "No Slow";
        case NO_STUN -> "No Stun"; case NO_FALL -> "No Fall"; case CRITICALS -> "Criticals"; case AUTO_TOTEM -> "Auto Totem"; case ATTRIBUTE_SWAP -> "Attribute Swap"; case KILL_AURA -> "Kill Aura"; case REACH -> "Reach"; case INFINITE_REACH -> "Infinite Reach"; case SPEED -> "Speed"; case MACE_KILL -> "Mace Kill"; case SUPER_PUNCH -> "Super Punch"; case AIR_JUMP -> "Air Jump"; case FREECAM -> "Freecam"; case FULLBRIGHT -> "Fullbright"; case FPS -> "FPS HUD";
        case COORDINATES -> "Coordinates";
    }; }
    private static String label(Category c) { return c.name().charAt(0) + c.name().substring(1).toLowerCase(); }
    private static boolean inside(double mx, double my, int x, int y, int w, int h) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    private static int opaque(int rgb) { return 0xFF000000 | (rgb & 0xFFFFFF); }
    private static int alpha(int rgb, int percent) { return (Math.max(0, Math.min(255, Math.round(percent * 2.55f))) << 24) | (rgb & 0xFFFFFF); }
    private static int nextThemeColor(int color) { int[] a={0xFFFF6B35,0xFFB00020,0xFF9C27B0,0xFF00BFA5,0xFFFFC107,0xFFFFFFFF}; for(int i=0;i<a.length;i++)if(a[i]==color)return a[(i+1)%a.length];return a[0]; }
    private static String colorName(int c) { return String.format("#%06X", c & 0xFFFFFF); }

    @Override
    public void onClose() {
        config.save();
        if (minecraft != null) minecraft.setScreenAndShow(parent);
    }
}
