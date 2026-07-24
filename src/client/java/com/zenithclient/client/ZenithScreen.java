package com.zenithclient.client;

import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/** ZenithClient's custom dark metallic Click GUI. */
public final class ZenithScreen extends Screen {
    private static final Identifier LOGO =
            Identifier.fromNamespaceAndPath(ZenithClient.MOD_ID, "textures/icon.png");

    private enum Category { VISUALS, COMBAT, MOVEMENT, HUD, CONFIG }
    private enum Module {
        PLAYER_ESP, ENTITY_OUTLINES, ITEM_ESP, PROJECTILE_ESP, BLOCK_OUTLINES,
        BOW_TRAJECTORY, XRAY, NO_BLINDNESS, NO_FIRE_OVERLAY,
        FLIGHT, SPEED, AUTO_SPRINT, NO_SLOW, NO_STUN, NO_FALL, AIR_JUMP, FREECAM,
        CRITICALS, AUTO_TOTEM, ATTRIBUTE_SWAP, KILL_AURA, REACH, INFINITE_REACH,
        MACE_KILL, FULLBRIGHT, FPS, COORDINATES
    }
    private enum HitType {
        TAB, MODULE, DONE, THEME_ACCENT, PANEL_OPACITY, BUTTON_OPACITY,
        CHAT_MESSAGES, RESET_THEME
    }

    private record Hitbox(HitType type, int value, int x, int y, int width, int height) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + width && my >= y && my < y + height;
        }
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
        int pw = Math.min(760, Math.max(560, width - 44));
        int ph = Math.min(430, Math.max(330, height - 44));
        int left = (width - pw) / 2;
        int top = (height - ph) / 2;
        int sidebar = 154;

        int accent = opaque(config.uiAccentColor);
        int accentSoft = alpha(accent, 34);
        int accentGlow = alpha(accent, 18);
        int panel = alpha(0xFF101216, config.uiPanelOpacity);
        int side = alpha(0xFF171A20, config.uiPanelOpacity);

        hitboxes.clear();

        // Dimmed world, outer shadow, and restrained metallic frame.
        g.fill(0, 0, width, height, 0xB008090B);
        g.fill(left - 7, top - 7, left + pw + 7, top + ph + 7, 0x36000000);
        g.fill(left - 3, top - 3, left + pw + 3, top + ph + 3, accentGlow);
        g.fill(left, top, left + pw, top + ph, panel);
        g.fill(left, top, left + sidebar, top + ph, side);

        // Header and brand line.
        g.fill(left, top, left + pw, top + 2, accent);
        g.fill(left + sidebar, top + 2, left + pw, top + 3, alpha(accent, 42));
        g.fill(left + sidebar - 1, top + 14, left + sidebar, top + ph - 14, alpha(accent, 38));

        g.fill(left + 12, top + 12, left + 60, top + 60, 0x44000000);
        g.fill(left + 13, top + 13, left + 59, top + 59, accentSoft);
        g.blit(RenderPipelines.GUI_TEXTURED, LOGO, left + 13, top + 13,
                0, 0, 46, 46, 256, 256, 256, 256, 0xFFFFFFFF);

        g.text(font, "ZENITH", left + 68, top + 20, 0xFFFFFFFF, true);
        g.text(font, "CLIENT", left + 68, top + 32, accent, true);
        g.text(font, ZenithClient.versionLabel(), left + 68, top + 46, 0xFF9299A3, false);

        int ty = top + 78;
        for (Category c : Category.values()) {
            boolean active = c == selectedCategory;
            boolean hover = inside(mouseX, mouseY, left + 12, ty, sidebar - 24, 30);

            int bg = active ? alpha(accent, 26) : hover ? 0x263B4048 : 0x0A000000;
            if (active) {
                g.fill(left + 12, ty, left + sidebar - 12, ty + 30, bg);
                g.fill(left + 12, ty + 4, left + 15, ty + 26, accent);
                g.fill(left + 15, ty + 29, left + sidebar - 12, ty + 30, accentSoft);
            } else if (hover) {
                g.fill(left + 12, ty, left + sidebar - 12, ty + 30, bg);
            }

            String tab = label(c);
            g.text(font, tab, left + 27, ty + 11,
                    active ? 0xFFFFFFFF : hover ? 0xFFE8EAED : 0xFF9AA0A9, active);
            hitboxes.add(new Hitbox(HitType.TAB, c.ordinal(), left + 12, ty, sidebar - 24, 30));
            ty += 38;
        }

        g.text(font, "LEFT CLICK  Toggle", left + 15, top + ph - 38, 0xFF686E77, false);
        g.text(font, "RIGHT CLICK Settings", left + 15, top + ph - 25, 0xFF686E77, false);

        int cx = left + sidebar + 22;
        int cy = top + 70;
        int cw = pw - sidebar - 42;

        g.text(font, label(selectedCategory).toUpperCase(), cx, top + 22, 0xFFFFFFFF, true);
        g.text(font, subtitle(selectedCategory), cx, top + 38, 0xFF858C96, false);
        g.fill(cx, top + 56, left + pw - 20, top + 57, 0x243B4048);
        g.fill(cx, top + 56, cx + Math.min(92, cw / 3), top + 57, accent);

        if (selectedCategory == Category.CONFIG) {
            drawConfig(g, mouseX, mouseY, cx, cy, cw, accent);
        } else {
            List<Module> modules = modules(selectedCategory);
            int gap = 12;
            int bw = (cw - gap) / 2;
            for (int i = 0; i < modules.size(); i++) {
                Module module = modules.get(i);
                int x = cx + (i % 2) * (bw + gap);
                int y = cy + (i / 2) * 52;
                drawModule(g, mouseX, mouseY, module, x, y, bw, 42, accent);
            }
        }

        int dx = left + pw - 96;
        int dy = top + ph - 31;
        boolean hoverDone = inside(mouseX, mouseY, dx, dy, 76, 21);
        g.fill(dx - 1, dy - 1, dx + 77, dy + 22, hoverDone ? alpha(accent, 44) : 0x28000000);
        customButton(g, dx, dy, 76, 21, "DONE",
                hoverDone ? alpha(accent, 62) : alpha(accent, 32), 0xFFFFFFFF, true);
        hitboxes.add(new Hitbox(HitType.DONE, 0, dx, dy, 76, 21));

        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    private void drawModule(GuiGraphicsExtractor g, int mx, int my, Module m,
                            int x, int y, int w, int h, int accent) {
        boolean enabled = enabled(m);
        boolean hover = inside(mx, my, x, y, w, h);

        int card = enabled ? alpha(accent, 21) : hover ? 0xD01B1E23 : 0xD014161A;
        int border = enabled ? alpha(accent, 62) : hover ? 0x4D69717B : 0x26444A52;

        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, border);
        g.fill(x, y, x + w, y + h, card);
        g.fill(x, y, x + 3, y + h, enabled ? accent : 0xFF4A5058);

        if (hover) {
            g.fill(x + 3, y, x + w, y + 1, 0x3AFFFFFF);
            g.fill(x + 3, y + h - 1, x + w, y + h, 0x22000000);
        }

        int textMax = Math.max(54, w - 68);
        g.text(font, fit(moduleName(m), textMax), x + 12, y + 9,
                enabled ? 0xFFFFFFFF : 0xFFD8DADD, true);
        g.text(font, enabled ? "ACTIVE" : "INACTIVE", x + 12, y + 25,
                enabled ? accent : 0xFF7A818A, false);

        int sx = x + w - 42;
        int sy = y + 13;
        g.fill(sx - 1, sy - 1, sx + 31, sy + 15, enabled ? alpha(accent, 52) : 0x443A3E44);
        g.fill(sx, sy, sx + 30, sy + 14, enabled ? alpha(accent, 88) : 0xFF383C42);
        int knobX = enabled ? sx + 17 : sx + 2;
        g.fill(knobX, sy + 2, knobX + 11, sy + 12, 0xFFFFFFFF);
        g.fill(knobX + 1, sy + 3, knobX + 10, sy + 11,
                enabled ? 0xFFF7F8FF : 0xFFC8CBD0);

        hitboxes.add(new Hitbox(HitType.MODULE, m.ordinal(), x, y, w, h));
    }

    private void drawConfig(GuiGraphicsExtractor g, int mx, int my,
                            int x, int y, int w, int accent) {
        int gap = 12;
        int bw = (w - gap) / 2;
        configButton(g, mx, my, HitType.THEME_ACCENT, x, y, bw,
                "Accent color", colorName(config.uiAccentColor), accent);
        configButton(g, mx, my, HitType.PANEL_OPACITY, x + bw + gap, y, bw,
                "Panel opacity", config.uiPanelOpacity + "%", accent);
        configButton(g, mx, my, HitType.BUTTON_OPACITY, x, y + 54, bw,
                "Card opacity", config.uiButtonOpacity + "%", accent);
        configButton(g, mx, my, HitType.CHAT_MESSAGES, x + bw + gap, y + 54, bw,
                "Chat messages", config.chatToggleMessages ? "ON" : "OFF", accent);
        configButton(g, mx, my, HitType.RESET_THEME, x, y + 108, bw,
                "Zenith theme", "RESTORE", accent);
    }

    private void configButton(GuiGraphicsExtractor g, int mx, int my, HitType type,
                              int x, int y, int w, String title, String value, int accent) {
        boolean hover = inside(mx, my, x, y, w, 42);
        g.fill(x - 1, y - 1, x + w + 1, y + 43, hover ? alpha(accent, 40) : 0x26444A52);
        g.fill(x, y, x + w, y + 42, hover ? 0xE01B1E23 : 0xE014161A);
        g.fill(x, y, x + 3, y + 42, accent);
        g.text(font, title, x + 12, y + 9, 0xFFF2F3F5, true);
        g.text(font, value, x + 12, y + 25, accent, false);
        hitboxes.add(new Hitbox(type, 0, x, y, w, 42));
    }

    private void customButton(GuiGraphicsExtractor g, int x, int y, int w, int h,
                              String text, int bg, int fg, boolean shadow) {
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
                case TAB -> {
                    selectedCategory = Category.values()[h.value];
                    config.lastUiTab = h.value;
                }
                case DONE -> {
                    onClose();
                    return true;
                }
                case THEME_ACCENT -> config.uiAccentColor = nextThemeColor(config.uiAccentColor);
                case PANEL_OPACITY -> {
                    config.uiPanelOpacity += 5;
                    if (config.uiPanelOpacity > 100) config.uiPanelOpacity = 70;
                }
                case BUTTON_OPACITY -> {
                    config.uiButtonOpacity += 5;
                    if (config.uiButtonOpacity > 100) config.uiButtonOpacity = 55;
                }
                case CHAT_MESSAGES -> config.chatToggleMessages = !config.chatToggleMessages;
                case RESET_THEME -> {
                    config.uiAccentColor = 0xFF2F8CFF;
                    config.uiPanelColor = 0xFF101216;
                    config.uiSidebarColor = 0xFF171A20;
                    config.uiPanelOpacity = 96;
                    config.uiButtonOpacity = 90;
                }
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
            case AIR_JUMP -> config.airJump;
            case FREECAM -> config.freecam;
            case FULLBRIGHT -> config.fullbright;
            case FPS -> config.showFps;
            case COORDINATES -> config.showCoordinates;
        };
    }

    private static List<Module> modules(Category c) {
        return switch (c) {
            case VISUALS -> List.of(
                    Module.PLAYER_ESP, Module.ENTITY_OUTLINES, Module.ITEM_ESP,
                    Module.PROJECTILE_ESP, Module.BLOCK_OUTLINES, Module.BOW_TRAJECTORY,
                    Module.XRAY, Module.NO_BLINDNESS, Module.NO_FIRE_OVERLAY);
            case COMBAT -> List.of(
                    Module.CRITICALS, Module.AUTO_TOTEM, Module.ATTRIBUTE_SWAP,
                    Module.KILL_AURA, Module.REACH, Module.INFINITE_REACH, Module.MACE_KILL);
            case MOVEMENT -> List.of(
                    Module.FLIGHT, Module.SPEED, Module.AUTO_SPRINT, Module.NO_SLOW,
                    Module.NO_STUN, Module.NO_FALL, Module.AIR_JUMP, Module.FREECAM);
            case HUD -> List.of(Module.FULLBRIGHT, Module.FPS, Module.COORDINATES);
            case CONFIG -> List.of();
        };
    }

    private static String moduleName(Module m) {
        return switch (m) {
            case PLAYER_ESP -> "Player ESP";
            case ENTITY_OUTLINES -> "Entity ESP";
            case ITEM_ESP -> "Item ESP";
            case PROJECTILE_ESP -> "Projectile ESP";
            case BLOCK_OUTLINES -> "Block ESP";
            case BOW_TRAJECTORY -> "Trajectories";
            case XRAY -> "X-Ray";
            case NO_BLINDNESS -> "No Blindness";
            case NO_FIRE_OVERLAY -> "No Fire Overlay";
            case FLIGHT -> "Flight";
            case AUTO_SPRINT -> "Auto Sprint";
            case NO_SLOW -> "No Slow";
            case NO_STUN -> "No Stun";
            case NO_FALL -> "No Fall";
            case CRITICALS -> "Criticals";
            case AUTO_TOTEM -> "Auto Totem";
            case ATTRIBUTE_SWAP -> "Attribute Swap";
            case KILL_AURA -> "Kill Aura";
            case REACH -> "Reach";
            case INFINITE_REACH -> "Infinite Reach";
            case SPEED -> "Speed";
            case MACE_KILL -> "Mace Kill";
            case AIR_JUMP -> "Air Jump";
            case FREECAM -> "Freecam";
            case FULLBRIGHT -> "Fullbright";
            case FPS -> "FPS HUD";
            case COORDINATES -> "Coordinates";
        };
    }

    private static String subtitle(Category c) {
        return switch (c) {
            case VISUALS -> "ESP, highlighting, overlays and world rendering";
            case COMBAT -> "Combat helpers and configurable attack utilities";
            case MOVEMENT -> "Movement, flight and physics controls";
            case HUD -> "Clean information overlays";
            case CONFIG -> "Theme and client presentation";
        };
    }

    private static String label(Category c) {
        return c.name().charAt(0) + c.name().substring(1).toLowerCase();
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static int opaque(int rgb) {
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }

    private static int alpha(int rgb, int percent) {
        return (Math.max(0, Math.min(255, Math.round(percent * 2.55f))) << 24)
                | (rgb & 0xFFFFFF);
    }

    private static int nextThemeColor(int color) {
        int[] colors = {
                0xFF2F8CFF, 0xFF55B7FF, 0xFF00C8FF,
                0xFFB8C0CC, 0xFFE6E9ED, 0xFFFFFFFF
        };
        for (int i = 0; i < colors.length; i++) {
            if (colors[i] == color) return colors[(i + 1) % colors.length];
        }
        return colors[0];
    }

    private static String colorName(int color) {
        return String.format("#%06X", color & 0xFFFFFF);
    }

    private String fit(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        String suffix = "...";
        int suffixWidth = font.width(suffix);
        String clipped = text;
        while (!clipped.isEmpty() && font.width(clipped) + suffixWidth > maxWidth) {
            clipped = clipped.substring(0, clipped.length() - 1);
        }
        return clipped.isEmpty() ? suffix : clipped + suffix;
    }

    @Override
    public void onClose() {
        config.save();
        if (minecraft != null) minecraft.setScreenAndShow(parent);
    }
}
