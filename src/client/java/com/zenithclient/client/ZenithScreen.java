package com.zenithclient.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/** ZenithClient Click GUI. */
public final class ZenithScreen extends Screen {
    private static final Identifier LOGO =
            Identifier.fromNamespaceAndPath(ZenithClient.MOD_ID, "textures/icon.png");
    private static final Identifier BANNER =
            Identifier.fromNamespaceAndPath(ZenithClient.MOD_ID, "textures/zenith_banner.png");

    private static final int BRAND_ORANGE = 0xFFFF5A1F;
    private static final int BRAND_AMBER = 0xFFFFA12B;
    private static final int LEGACY_BLUE = 0xFF2F8CFF;

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
    private float bannerReveal;
    private long lastFrameNanos;

    public ZenithScreen(Screen parent, ZenithConfig config) {
        super(Component.literal("ZenithClient"));
        this.parent = parent;
        this.config = config;
        migrateLegacyTheme();
        Category[] tabs = Category.values();
        selectedCategory = tabs[Math.max(0, Math.min(tabs.length - 1, config.lastUiTab))];
    }

    private void migrateLegacyTheme() {
        if (config.uiAccentColor == LEGACY_BLUE) {
            config.uiAccentColor = BRAND_ORANGE;
            config.uiPanelColor = 0xFF0B0C0E;
            config.uiSidebarColor = 0xFF101216;
            config.uiPanelOpacity = Math.max(92, config.uiPanelOpacity);
            config.uiButtonOpacity = Math.max(86, config.uiButtonOpacity);
            config.save();
        }
    }

    @Override
    protected void init() {
        hitboxes.clear();
        lastFrameNanos = System.nanoTime();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        int pw = Math.min(820, Math.max(420, width - 24));
        int ph = Math.min(460, Math.max(280, height - 24));
        pw = Math.min(pw, Math.max(1, width - 12));
        ph = Math.min(ph, Math.max(1, height - 12));
        int left = Math.max(6, (width - pw) / 2);
        int top = Math.max(6, (height - ph) / 2);
        int sidebar = Math.max(132, Math.min(168, pw / 4));
        boolean compact = ph < 390;

        int accent = opaque(config.uiAccentColor);
        int panel = alpha(0xFF090A0C, config.uiPanelOpacity);
        int side = alpha(0xFF111318, config.uiPanelOpacity);
        int cardOpacity = Math.max(45, Math.min(100, config.uiButtonOpacity));

        hitboxes.clear();
        updateBannerAnimation(inside(mouseX, mouseY, left + 14, top + 13, 52, 52));

        // Dark world fade and deep shadow.
        g.fill(0, 0, width, height, 0xC0060708);
        g.fill(left - 9, top - 9, left + pw + 9, top + ph + 9, 0x46000000);
        g.fill(left - 5, top - 5, left + pw + 5, top + ph + 5, alpha(accent, 12));

        // Main shell with the thin orange frame used by the supplied banner.
        g.fill(left, top, left + pw, top + ph, panel);
        g.fill(left, top, left + sidebar, top + ph, side);
        drawFrame(g, left, top, pw, ph, accent);
        g.fill(left + sidebar, top + 1, left + sidebar + 1, top + ph - 1, alpha(accent, 34));

        // Header strip.
        g.fill(left + sidebar, top + 1, left + pw - 1, top + 62, 0xD00D0F12);
        g.fill(left + sidebar, top + 61, left + pw - 1, top + 62, alpha(accent, 45));
        g.fill(left + sidebar + 20, top + 56, left + sidebar + 124, top + 58, accent);

        // Compact logo button.
        g.fill(left + 13, top + 12, left + 67, top + 66, 0xFF08090B);
        drawFrame(g, left + 13, top + 12, 54, 54, accent);
        g.blit(RenderPipelines.GUI_TEXTURED, LOGO, left + 17, top + 16,
                0, 0, 46, 46, 256, 256, 256, 256, 0xFFFFFFFF);

        g.text(font, "ZENITH", left + 76, top + 21, accent, true);
        g.text(font, "CLIENT", left + 76, top + 34, 0xFFF4F4F5, true);
        g.text(font, ZenithClient.versionLabel(), left + 76, top + 49, 0xFF777D86, false);

        int tabHeight = compact ? 25 : 32;
        int tabStep = compact ? 29 : 39;
        int ty = top + (compact ? 72 : 82);
        for (Category c : Category.values()) {
            boolean active = c == selectedCategory;
            boolean hover = inside(mouseX, mouseY, left + 13, ty, sidebar - 26, tabHeight);
            if (active || hover) {
                g.fill(left + 13, ty, left + sidebar - 13, ty + tabHeight,
                        active ? alpha(accent, 20) : 0xC017191D);
                g.fill(left + 13, ty, left + 16, ty + tabHeight, active ? accent : alpha(accent, 55));
                g.fill(left + 16, ty + tabHeight - 1, left + sidebar - 13, ty + tabHeight,
                        active ? alpha(accent, 55) : 0x243A3D42);
            }
            String tab = label(c).toUpperCase();
            g.text(font, tab, left + 28, ty + (tabHeight - 8) / 2,
                    active ? 0xFFFFFFFF : hover ? 0xFFF0F0F1 : 0xFF888E97, active);
            hitboxes.add(new Hitbox(HitType.TAB, c.ordinal(), left + 13, ty, sidebar - 26, tabHeight));
            ty += tabStep;
        }

        if (!compact) {
            g.fill(left + 13, top + ph - 57, left + sidebar - 13, top + ph - 56, 0x263A3D42);
            g.text(font, "LEFT CLICK", left + 16, top + ph - 45, accent, true);
            g.text(font, "Toggle", left + 78, top + ph - 45, 0xFF777D86, false);
            g.text(font, "RIGHT CLICK", left + 16, top + ph - 31, 0xFFF2F2F3, true);
            g.text(font, "Settings", left + 88, top + ph - 31, 0xFF777D86, false);
        }

        int cx = left + sidebar + (compact ? 14 : 22);
        int cy = top + (compact ? 68 : 78);
        int cw = pw - sidebar - (compact ? 28 : 44);
        g.text(font, label(selectedCategory).toUpperCase(), cx, top + 20, 0xFFFFFFFF, true);
        g.text(font, subtitle(selectedCategory), cx, top + 36, 0xFF858B94, false);

        int pillW = 88;
        g.fill(left + pw - pillW - 18, top + 17, left + pw - 18, top + 42, 0xFF111318);
        drawFrame(g, left + pw - pillW - 18, top + 17, pillW, 25, alpha(accent, 65));
        g.text(font, "RIGHT SHIFT", left + pw - pillW - 9, top + 26, accent, true);

        if (selectedCategory == Category.CONFIG) {
            drawConfig(g, mouseX, mouseY, cx, cy, cw, accent);
        } else {
            List<Module> modules = modules(selectedCategory);
            int gap = compact ? 7 : 12;
            int bw = (cw - gap) / 2;
            int rows = Math.max(1, (modules.size() + 1) / 2);
            int available = Math.max(1, top + ph - 48 - cy);
            int cardH = Math.max(28, Math.min(42, (available - gap * (rows - 1)) / rows));
            int rowStep = cardH + gap;
            for (int i = 0; i < modules.size(); i++) {
                Module module = modules.get(i);
                int x = cx + (i % 2) * (bw + gap);
                int y = cy + (i / 2) * rowStep;
                drawModule(g, mouseX, mouseY, module, x, y, bw, cardH, accent, cardOpacity);
            }
        }

        int dx = left + pw - 98;
        int dy = top + ph - 32;
        boolean hoverDone = inside(mouseX, mouseY, dx, dy, 78, 22);
        g.fill(dx, dy, dx + 78, dy + 22, hoverDone ? accent : 0xFF111318);
        drawFrame(g, dx, dy, 78, 22, hoverDone ? BRAND_AMBER : accent);
        g.text(font, "DONE", dx + (78 - font.width("DONE")) / 2, dy + 7,
                hoverDone ? 0xFF08090B : 0xFFFFFFFF, true);
        hitboxes.add(new Hitbox(HitType.DONE, 0, dx, dy, 78, 22));

        // The banner is drawn last so it visibly unfolds over the window.
        drawExpandingBanner(g, left, top, accent);
        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    private void updateBannerAnimation(boolean hovered) {
        long now = System.nanoTime();
        if (lastFrameNanos == 0L) lastFrameNanos = now;
        float dt = Math.min(0.05F, (now - lastFrameNanos) / 1_000_000_000.0F);
        lastFrameNanos = now;
        float target = hovered ? 1.0F : 0.0F;
        float amount = Math.min(1.0F, dt * 11.0F);
        bannerReveal += (target - bannerReveal) * amount;
        if (Math.abs(target - bannerReveal) < 0.002F) bannerReveal = target;
    }

    private void drawExpandingBanner(GuiGraphicsExtractor g, int left, int top, int accent) {
        if (bannerReveal <= 0.01F) return;
        int x = left + 13;
        int y = top + 12;
        int fixedHeight = 54;
        int collapsedW = 54;
        int fullBannerW = 180;
        int revealW = collapsedW + Math.round((fullBannerW - collapsedW) * easeOut(bannerReveal));

        // Keep height fixed and reveal only additional pixels to the right.
        g.fill(x - 4, y - 4, x + revealW + 4, y + fixedHeight + 4, 0xB8000000);
        g.enableScissor(x, y, x + revealW, y + fixedHeight);
        g.fill(x, y, x + fullBannerW, y + fixedHeight, 0xFF07080A);
        g.blit(RenderPipelines.GUI_TEXTURED, BANNER, x, y,
                0, 0, fullBannerW, fixedHeight,
                1024, 341, 1024, 341, 0xFFFFFFFF);
        drawFrame(g, x, y, fullBannerW, fixedHeight, accent);
        g.disableScissor();
    }

    private static float easeOut(float value) {
        float inv = 1.0F - Math.max(0.0F, Math.min(1.0F, value));
        return 1.0F - inv * inv * inv;
    }

    private void drawModule(GuiGraphicsExtractor g, int mx, int my, Module m,
                            int x, int y, int w, int h, int accent, int cardOpacity) {
        boolean enabled = enabled(m);
        boolean hover = inside(mx, my, x, y, w, h);
        int bg = enabled ? alpha(accent, 13) : alpha(0xFF111318, cardOpacity);
        int border = enabled ? accent : hover ? alpha(accent, 70) : 0xFF2B2E34;

        g.fill(x, y, x + w, y + h, bg);
        drawFrame(g, x, y, w, h, border);
        g.fill(x, y, x + 3, y + h, enabled ? accent : 0xFF454A52);
        if (hover) g.fill(x + 4, y + 1, x + w - 1, y + 2, 0x44FFFFFF);

        int textMax = Math.max(54, w - 72);
        g.text(font, fit(moduleName(m), textMax), x + 12, y + 9,
                enabled || hover ? 0xFFFFFFFF : 0xFFD3D5D8, true);
        g.text(font, enabled ? "ENABLED" : "DISABLED", x + 12, y + 25,
                enabled ? accent : hover ? 0xFFB2B5BA : 0xFF707680, false);

        int sx = x + w - 44;
        int sy = y + 13;
        g.fill(sx, sy, sx + 32, sy + 15, enabled ? alpha(accent, 78) : 0xFF2C3036);
        drawFrame(g, sx, sy, 32, 15, enabled ? accent : 0xFF4B5058);
        int knobX = enabled ? sx + 18 : sx + 2;
        g.fill(knobX, sy + 2, knobX + 12, sy + 13, enabled ? 0xFFFFFFFF : 0xFFC4C7CC);
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
        g.fill(x, y, x + w, y + 42, hover ? alpha(accent, 15) : 0xE0111317);
        drawFrame(g, x, y, w, 42, hover ? accent : 0xFF30343A);
        g.fill(x, y, x + 3, y + 42, accent);
        g.text(font, title, x + 12, y + 9, 0xFFF2F3F5, true);
        g.text(font, value, x + 12, y + 25, hover ? 0xFFFFFFFF : accent, false);
        hitboxes.add(new Hitbox(type, 0, x, y, w, 42));
    }

    private static void drawFrame(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
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
                    config.uiAccentColor = BRAND_ORANGE;
                    config.uiPanelColor = 0xFF090A0C;
                    config.uiSidebarColor = 0xFF111318;
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
                BRAND_ORANGE, BRAND_AMBER, 0xFFFF3B30,
                0xFFFFFFFF, 0xFFB8C0CC, 0xFF00C8FF
        };
        for (int i = 0; i < colors.length; i++) {
            if ((colors[i] & 0xFFFFFF) == (color & 0xFFFFFF)) {
                return colors[(i + 1) % colors.length];
            }
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
