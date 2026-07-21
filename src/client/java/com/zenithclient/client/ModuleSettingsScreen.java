package com.zenithclient.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Custom-drawn per-module settings screen. Right-click a module card to open it. */
public final class ModuleSettingsScreen extends Screen {
    public enum Type { PLAYER, ENTITY, BLOCKS, TRAJECTORY, XRAY, FLIGHT, AUTO_SPRINT, NO_SLOW, NO_STUN, NO_FALL, CRITICALS, AIR_JUMP, FULLBRIGHT, FPS, COORDINATES }
    private enum Action { KEYBIND, OPTION, BACK }
    private record Hit(Action action, int index, int x, int y, int w, int h) {
        boolean contains(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }
    private record Numeric(double min, double max, double step, boolean integer) { }

    private final Screen parent;
    private final ZenithConfig config;
    private final Type type;
    private final List<Hit> hits = new ArrayList<>();
    private boolean capturingKey;
    private int editingNumber = -1;
    private String numberBuffer = "";
    private int scrollOffset;
    private int maxScroll;
    private boolean replaceNumberOnType;
    private int draggingNumber = -1;

    private ModuleSettingsScreen(Screen parent, ZenithConfig config, Type type) {
        super(Component.literal("Module Settings"));
        this.parent = parent;
        this.config = config;
        this.type = type;
    }

    public static ModuleSettingsScreen of(Screen parent, ZenithConfig config, Type type) {
        return new ModuleSettingsScreen(parent, config, type);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        int panelW = Math.min(520, width - 28);
        int panelH = Math.min(450, height - 24);
        int left = (width - panelW) / 2;
        int top = (height - panelH) / 2;
        int accent = opaque(config.uiAccentColor);
        int panel = alpha(config.uiPanelColor, config.uiPanelOpacity);

        hits.clear();
        g.fill(0, 0, width, height, 0x78000000);
        g.fill(left, top, left + panelW, top + panelH, panel);
        g.fill(left, top, left + panelW, top + 3, accent);
        g.text(font, title(), left + 18, top + 14, 0xFFF4F4F4, true);
        g.text(font, description(), left + 18, top + 30, 0xFFAAAAAA, false);

        int x = left + 18;
        int contentTop = top + 54;
        int contentBottom = top + panelH - 42;
        int w = panelW - 36;
        List<String[]> rows = optionRows();
        int totalHeight = 38 * (rows.size() + 1);
        maxScroll = Math.max(0, totalHeight - (contentBottom - contentTop));
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));
        int y = contentTop - scrollOffset;

        g.enableScissor(x, contentTop, x + w, contentBottom);
        if (y + 31 >= contentTop && y < contentBottom)
            drawRow(g, mouseX, mouseY, Action.KEYBIND, 0, x, y, w, "Keybind", capturingKey ? "PRESS A KEY..." : keyName(getKeybind()), accent, null);
        y += 38;
        for (int i = 0; i < rows.size(); i++) {
            if (y + 31 >= contentTop && y < contentBottom)
                drawRow(g, mouseX, mouseY, Action.OPTION, i, x, y, w, rows.get(i)[0], rows.get(i)[1], accent, numeric(i));
            y += 38;
        }
        g.disableScissor();

        if (maxScroll > 0) {
            int trackX = left + panelW - 8;
            int trackH = contentBottom - contentTop;
            int thumbH = Math.max(24, trackH * trackH / totalHeight);
            int thumbY = contentTop + (trackH - thumbH) * scrollOffset / maxScroll;
            g.fill(trackX, contentTop, trackX + 3, contentBottom, 0x44222222);
            g.fill(trackX, thumbY, trackX + 3, thumbY + thumbH, accent);
        }

        int bx = left + panelW - 92;
        int by = top + panelH - 30;
        drawSmall(g, mouseX, mouseY, Action.BACK, 0, bx, by, 74, 20, "Back", accent);
        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    private void drawRow(GuiGraphicsExtractor g, int mx, int my, Action action, int index,
                         int x, int y, int w, String label, String value, int accent, Numeric numeric) {
        boolean hover = inside(mx, my, x, y, w, 31);
        int bg = hover ? alpha(accent, 92) : alpha(0xFF272727, config.uiButtonOpacity);
        g.fill(x, y, x + w, y + 33, bg);
        g.fill(x, y, x + 3, y + 33, accent);
        g.text(font, label, x + 11, y + 7, 0xFFF0F0F0, true);

        String shown = editingNumber == index && action == Action.OPTION ? numberBuffer + "_" : value;
        int tw = font.width(shown);
        int valueColor = hover ? 0xFFFFFFFF : (capturingKey && action == Action.KEYBIND ? 0xFFFFFF66 : accent);
        if (numeric != null) {
            int boxW = Math.max(54, tw + 12);
            int boxX = x + w - boxW - 8;
            g.fill(boxX, y + 5, boxX + boxW, y + 18, editingNumber == index ? 0xAA111111 : 0x66222222);
            g.fill(boxX, y + 18, boxX + boxW, y + 19, editingNumber == index ? accent : 0x77555555);
            g.text(font, shown, boxX + boxW - tw - 6, y + 7, valueColor, false);
        } else {
            g.text(font, shown, x + w - tw - 11, y + 7, valueColor, false);
        }

        if (numeric != null) {
            int sx = x + 11;
            int sw = w - 22;
            int sy = y + 24;
            double valueNow = numericValue(index);
            double ratio = (valueNow - numeric.min) / (numeric.max - numeric.min);
            ratio = Math.max(0, Math.min(1, ratio));
            int knobX = sx + (int) Math.round(sw * ratio);
            g.fill(sx, sy, sx + sw, sy + 4, 0x88555555);
            g.fill(sx, sy, knobX, sy + 4, hover ? 0xFFFFFFFF : accent);
            g.fill(knobX - 2, sy - 2, knobX + 3, sy + 6, 0xFFFFFFFF);
        }

        if (hover) g.fill(x, y, x + w, y + 1, 0x88FFFFFF);
        hits.add(new Hit(action, index, x, y, w, 33));
    }

    private void drawSmall(GuiGraphicsExtractor g, int mx, int my, Action action, int index,
                           int x, int y, int w, int h, String text, int accent) {
        boolean hover = inside(mx, my, x, y, w, h);
        g.fill(x, y, x + w, y + h, hover ? alpha(accent, 170) : alpha(accent, 95));
        g.text(font, text, x + (w - font.width(text)) / 2, y + 6, 0xFFFFFFFF, true);
        hits.add(new Hit(action, index, x, y, w, h));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        for (Hit hit : hits) {
            if (!hit.contains(event.x(), event.y())) continue;
            if (hit.action == Action.BACK) { commitNumber(); onClose(); return true; }
            if (hit.action == Action.KEYBIND) {
                commitNumber();
                capturingKey = true;
                return true;
            }
            if (hit.action == Action.OPTION) {
                Numeric numeric = numeric(hit.index);
                if (numeric != null) {
                    int sliderLeft = hit.x + 11;
                    int sliderWidth = hit.w - 22;
                    if (event.y() >= hit.y + 18) {
                        double ratio = Math.max(0, Math.min(1, (event.x() - sliderLeft) / (double) sliderWidth));
                        setNumeric(hit.index, numeric.min + ratio * (numeric.max - numeric.min));
                        draggingNumber = hit.index;
                        config.save();
                    }
                    editingNumber = hit.index;
                    numberBuffer = numeric.integer ? Integer.toString((int) Math.round(numericValue(hit.index))) : trim(numericValue(hit.index));
                    replaceNumberOnType = true;
                    return true;
                }
                commitNumber();
                if (hit.index == baseOptionCount()) config.chatToggleMessages = !config.chatToggleMessages;
                else changeOption(hit.index, event.buttonInfo().button() == 1 ? -1 : 1);
                config.save();
                if (type == Type.XRAY) ZenithClient.refreshWorldRenderer();
                return true;
            }
        }
        commitNumber();
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (draggingNumber >= 0) {
            for (Hit hit : hits) {
                if (hit.action == Action.OPTION && hit.index == draggingNumber) {
                    Numeric numeric = numeric(hit.index);
                    if (numeric == null) break;
                    int sliderLeft = hit.x + 11;
                    int sliderWidth = hit.w - 22;
                    double ratio = Math.max(0, Math.min(1, (event.x() - sliderLeft) / (double) sliderWidth));
                    setNumeric(hit.index, numeric.min + ratio * (numeric.max - numeric.min));
                    numberBuffer = numeric.integer ? Integer.toString((int) Math.round(numericValue(hit.index))) : trim(numericValue(hit.index));
                    replaceNumberOnType = true;
                    config.save();
                    if (type == Type.XRAY) ZenithClient.refreshWorldRenderer();
                    return true;
                }
            }
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        draggingNumber = -1;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (maxScroll > 0) {
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) Math.round(verticalAmount * 24.0)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (capturingKey) {
            int key = event.key();
            setKeybind(key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE ? -1 : key);
            capturingKey = false;
            config.save();
            return true;
        }
        if (editingNumber >= 0) {
            if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) { commitNumber(); return true; }
            if (event.key() == GLFW.GLFW_KEY_ESCAPE) { editingNumber = -1; numberBuffer = ""; replaceNumberOnType = false; return true; }
            if (event.key() == GLFW.GLFW_KEY_BACKSPACE) {
                if (replaceNumberOnType) { numberBuffer = ""; replaceNumberOnType = false; }
                else if (!numberBuffer.isEmpty()) numberBuffer = numberBuffer.substring(0, numberBuffer.length() - 1);
                return true;
            }
            int digit = -1;
            if (event.key() >= GLFW.GLFW_KEY_0 && event.key() <= GLFW.GLFW_KEY_9) digit = event.key() - GLFW.GLFW_KEY_0;
            else if (event.key() >= GLFW.GLFW_KEY_KP_0 && event.key() <= GLFW.GLFW_KEY_KP_9) digit = event.key() - GLFW.GLFW_KEY_KP_0;
            if (digit >= 0) { appendNumberCharacter((char) ('0' + digit)); return true; }
            if (event.key() == GLFW.GLFW_KEY_PERIOD || event.key() == GLFW.GLFW_KEY_KP_DECIMAL) { appendNumberCharacter('.'); return true; }
            if (event.key() == GLFW.GLFW_KEY_MINUS || event.key() == GLFW.GLFW_KEY_KP_SUBTRACT) { appendNumberCharacter('-'); return true; }
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent input) {
        if (editingNumber >= 0) {
            int c = input.codepoint();
            if ((c >= '0' && c <= '9') || c == '.' || c == '-') {
                appendNumberCharacter((char) c);
                return true;
            }
        }
        return super.charTyped(input);
    }

    private void appendNumberCharacter(char c) {
        if (replaceNumberOnType) {
            numberBuffer = "";
            replaceNumberOnType = false;
        }
        if (c == '-') {
            if (numberBuffer.isEmpty()) numberBuffer = "-";
            return;
        }
        if (c == '.' && numberBuffer.contains(".")) return;
        if (numberBuffer.length() < 12) numberBuffer += c;
    }

    private void commitNumber() {
        if (editingNumber < 0) return;
        try { setNumeric(editingNumber, Double.parseDouble(numberBuffer)); } catch (NumberFormatException ignored) { }
        editingNumber = -1;
        numberBuffer = "";
        replaceNumberOnType = false;
        config.save();
        if (type == Type.XRAY) ZenithClient.refreshWorldRenderer();
    }

    private Numeric numeric(int index) {
        return switch (type) {
            case PLAYER -> switch (index) { case 4 -> new Numeric(0, 100, 1, true); case 5 -> new Numeric(1, 10, 1, true); case 6 -> new Numeric(8, 256, 1, true); default -> null; };
            case ENTITY -> switch (index) { case 6 -> new Numeric(0, 100, 1, true); case 7 -> new Numeric(1, 10, 1, true); case 8 -> new Numeric(8, 256, 1, true); default -> null; };
            case BLOCKS -> switch (index) { case 1 -> new Numeric(4, 32, 1, true); case 3 -> new Numeric(0, 100, 1, true); default -> null; };
            case TRAJECTORY -> switch (index) { case 0 -> new Numeric(1, 12, 1, true); case 2 -> new Numeric(1, 6, 1, true); case 3 -> new Numeric(0, 8, 0.05, false); default -> null; };
            case XRAY -> switch (index) { case 1 -> new Numeric(0, 255, 1, true); default -> null; };
            case FLIGHT -> switch (index) { case 0, 1 -> new Numeric(0.1, 10, 0.1, false); case 2 -> new Numeric(1, 4, 0.1, false); default -> null; };
            default -> null;
        };
    }

    private double numericValue(int index) {
        return switch (type) {
            case PLAYER -> switch (index) { case 4 -> config.playerFillOpacity; case 5 -> config.playerOutlineThickness; case 6 -> config.entityRange; default -> 0; };
            case ENTITY -> switch (index) { case 6 -> config.entityFillOpacity; case 7 -> config.entityOutlineThickness; case 8 -> config.entityRange; default -> 0; };
            case BLOCKS -> switch (index) { case 1 -> config.blockRadius; case 3 -> config.blockFillOpacity; default -> 0; };
            case TRAJECTORY -> switch (index) { case 0 -> config.lineDensity; case 2 -> config.trajectoryThickness; case 3 -> config.trajectoryStartDistance; default -> 0; };
            case XRAY -> index == 1 ? config.xrayOpacity : 0;
            case FLIGHT -> switch (index) { case 0 -> config.flightSpeed; case 1 -> config.flightVerticalSpeed; case 2 -> config.flightSprintMultiplier; default -> 0; };
            default -> 0;
        };
    }

    private void setNumeric(int index, double raw) {
        Numeric n = numeric(index);
        if (n == null) return;
        double clamped = Math.max(n.min, Math.min(n.max, raw));
        double value = Math.round(clamped / n.step) * n.step;
        if (n.integer) value = Math.round(value);
        switch (type) {
            case PLAYER -> { if (index == 4) config.playerFillOpacity = (int) value; else if (index == 5) config.playerOutlineThickness = (int) value; else if (index == 6) config.entityRange = (int) value; }
            case ENTITY -> { if (index == 6) config.entityFillOpacity = (int) value; else if (index == 7) config.entityOutlineThickness = (int) value; else if (index == 8) config.entityRange = (int) value; }
            case BLOCKS -> { if (index == 1) config.blockRadius = (int) value; else if (index == 3) config.blockFillOpacity = (int) value; }
            case TRAJECTORY -> { if (index == 0) config.lineDensity = (int) value; else if (index == 2) config.trajectoryThickness = (int) value; else if (index == 3) config.trajectoryStartDistance = value; }
            case XRAY -> { if (index == 1) config.xrayOpacity = (int) value; }
            case FLIGHT -> { if (index == 0) config.flightSpeed = value; else if (index == 1) config.flightVerticalSpeed = value; else if (index == 2) config.flightSprintMultiplier = value; }
            default -> { }
        }
    }

    private List<String[]> optionRows() {
        List<String[]> rows = new ArrayList<>();
        switch (type) {
            case PLAYER -> { rows.add(row("ESP", onOff(config.playerEsp))); rows.add(row("Shape", config.playerEspShape.displayName())); rows.add(row("Outline color", colorName(config.playerOutlineColor))); rows.add(row("Fill color", colorName(config.playerFillColor))); rows.add(row("Fill opacity", config.playerFillOpacity + "%")); rows.add(row("Outline thickness", Integer.toString(config.playerOutlineThickness))); rows.add(row("Range", config.entityRange + " blocks")); rows.add(row("Tracers", onOff(config.playerTracers))); rows.add(row("Name tags", onOff(config.playerNameTags))); }
            case ENTITY -> { rows.add(row("Target group", config.entityHighlightMode.displayName())); rows.add(row("Shape", config.entityEspShape.displayName())); rows.add(row("Outline", onOff(config.entityOutline))); rows.add(row("Fill / chams", onOff(config.entityFill))); rows.add(row("Outline color", colorName(config.entityOutlineColor))); rows.add(row("Fill color", colorName(config.entityFillColor))); rows.add(row("Fill opacity", config.entityFillOpacity + "%")); rows.add(row("Outline thickness", Integer.toString(config.entityOutlineThickness))); rows.add(row("Range", config.entityRange + " blocks")); rows.add(row("Tracers", onOff(config.entityTracers))); rows.add(row("Name tags", onOff(config.entityNameTags))); }
            case BLOCKS -> { rows.add(row("Block type", config.blockHighlightMode.displayName())); rows.add(row("Search radius", config.blockRadius + " blocks")); rows.add(row("Outline color", colorName(config.blockOutlineColor))); rows.add(row("Fill opacity", config.blockFillOpacity + "%")); }
            case TRAJECTORY -> { rows.add(row("Simulation quality", Integer.toString(config.lineDensity))); rows.add(row("Line color", colorName(config.trajectoryColor))); rows.add(row("Thickness", Integer.toString(config.trajectoryThickness))); rows.add(row("Start distance", trim(config.trajectoryStartDistance))); }
            case XRAY -> { rows.add(row("Visible blocks", config.xrayMode.displayName())); rows.add(row("Hidden-block opacity", config.xrayOpacity + " / 255")); }
            case FLIGHT -> { rows.add(row("Horizontal speed", trim(config.flightSpeed))); rows.add(row("Vertical speed", trim(config.flightVerticalSpeed))); rows.add(row("Sprint multiplier", trim(config.flightSprintMultiplier) + "x")); }
            default -> { }
        }
        rows.add(row("Chat toggle messages", onOff(config.chatToggleMessages)));
        return rows;
    }

    private int baseOptionCount() {
        return switch (type) {
            case PLAYER -> 9; case ENTITY -> 11; case BLOCKS -> 4; case TRAJECTORY -> 4;
            case XRAY -> 2; case FLIGHT -> 3; default -> 0;
        };
    }

    private void changeOption(int index, int direction) {
        switch (type) {
            case PLAYER -> { if (index == 0) config.playerEsp = !config.playerEsp; else if (index == 1) config.playerEspShape = direction > 0 ? config.playerEspShape.next() : previous(config.playerEspShape); else if (index == 2) config.playerOutlineColor = cycleColor(config.playerOutlineColor, direction); else if (index == 3) config.playerFillColor = cycleColor(config.playerFillColor, direction); else if (index == 7) config.playerTracers = !config.playerTracers; else if (index == 8) config.playerNameTags = !config.playerNameTags; }
            case ENTITY -> { if (index == 0) config.entityHighlightMode = direction > 0 ? config.entityHighlightMode.next() : previous(config.entityHighlightMode); else if (index == 1) config.entityEspShape = direction > 0 ? config.entityEspShape.next() : previous(config.entityEspShape); else if (index == 2) config.entityOutline = !config.entityOutline; else if (index == 3) config.entityFill = !config.entityFill; else if (index == 4) config.entityOutlineColor = cycleColor(config.entityOutlineColor, direction); else if (index == 5) config.entityFillColor = cycleColor(config.entityFillColor, direction); else if (index == 9) config.entityTracers = !config.entityTracers; else if (index == 10) config.entityNameTags = !config.entityNameTags; }
            case BLOCKS -> { if (index == 0) config.blockHighlightMode = direction > 0 ? config.blockHighlightMode.next() : previous(config.blockHighlightMode); else if (index == 2) config.blockOutlineColor = cycleColor(config.blockOutlineColor, direction); }
            case TRAJECTORY -> { if (index == 1) config.trajectoryColor = cycleColor(config.trajectoryColor, direction); }
            case XRAY -> { if (index == 0) config.xrayMode = direction > 0 ? config.xrayMode.next() : previous(config.xrayMode); }
            default -> { }
        }
    }

    private int getKeybind() { return switch (type) { case PLAYER -> config.playerEspKey; case ENTITY -> config.entityHighlightsKey; case BLOCKS -> config.blockHighlightsKey; case TRAJECTORY -> config.trajectoryPreviewKey; case XRAY -> config.xrayKey; case FLIGHT -> config.flightKey; case AUTO_SPRINT -> config.autoSprintKey; case NO_SLOW -> config.noSlowKey; case NO_STUN -> config.noStunKey; case NO_FALL -> config.noFallKey; case CRITICALS -> config.criticalsKey; case AIR_JUMP -> config.airJumpKey; case FULLBRIGHT -> config.fullbrightKey; case FPS -> config.showFpsKey; case COORDINATES -> config.showCoordinatesKey; }; }
    private void setKeybind(int key) { switch (type) { case PLAYER -> config.playerEspKey = key; case ENTITY -> config.entityHighlightsKey = key; case BLOCKS -> config.blockHighlightsKey = key; case TRAJECTORY -> config.trajectoryPreviewKey = key; case XRAY -> config.xrayKey = key; case FLIGHT -> config.flightKey = key; case AUTO_SPRINT -> config.autoSprintKey = key; case NO_SLOW -> config.noSlowKey = key; case NO_STUN -> config.noStunKey = key; case NO_FALL -> config.noFallKey = key; case CRITICALS -> config.criticalsKey = key; case AIR_JUMP -> config.airJumpKey = key; case FULLBRIGHT -> config.fullbrightKey = key; case FPS -> config.showFpsKey = key; case COORDINATES -> config.showCoordinatesKey = key; } }

    private String title() { return switch (type) { case PLAYER -> "Player ESP Settings"; case ENTITY -> "Entity ESP Settings"; case BLOCKS -> "Block ESP Settings"; case TRAJECTORY -> "Trajectory Settings"; case XRAY -> "X-Ray Settings"; case FLIGHT -> "Flight Settings"; case AUTO_SPRINT -> "Auto Sprint Settings"; case NO_SLOW -> "No Slow Settings"; case NO_STUN -> "No Stun Settings"; case NO_FALL -> "No Fall Settings"; case CRITICALS -> "Criticals Settings"; case AIR_JUMP -> "Air Jump Settings"; case FULLBRIGHT -> "Fullbright Settings"; case FPS -> "FPS HUD Settings"; case COORDINATES -> "Coordinates HUD Settings"; }; }
    private String description() { return switch (type) { case PLAYER -> "Highlights other players through walls."; case ENTITY -> "Highlights selected entity types through walls."; case BLOCKS -> "Shows selected nearby blocks with an overlay."; case TRAJECTORY -> "Predicts projectile paths and collision targets."; case XRAY -> "Only renders ores; opacity controls every other block."; case FLIGHT -> "Controls horizontal, vertical, and sprint flight speed."; default -> "Click the keybind row to bind a key."; }; }

    private static String[] row(String a, String b) { return new String[]{a, b}; }
    private static String onOff(boolean value) { return value ? "ON" : "OFF"; }
    private static boolean inside(double mx, double my, int x, int y, int w, int h) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    private static int opaque(int color) { return 0xFF000000 | (color & 0x00FFFFFF); }
    private static int alpha(int color, int percent) { return (Math.max(0, Math.min(255, Math.round(percent * 2.55F))) << 24) | (color & 0x00FFFFFF); }
    private static int cycleColor(int color, int direction) { int[] colors = {0xFF3B30, 0x00E5FF, 0x39FF14, 0xFFD60A, 0xBF5AF2, 0xFFFFFF, 0xFF6B35}; int at = 0; for (int i = 0; i < colors.length; i++) if ((colors[i] & 0xFFFFFF) == (color & 0xFFFFFF)) at = i; return colors[Math.floorMod(at + direction, colors.length)]; }
    private static String colorName(int color) { return switch (color & 0xFFFFFF) { case 0xFF3B30 -> "Red"; case 0x00E5FF -> "Cyan"; case 0x39FF14 -> "Green"; case 0xFFD60A -> "Yellow"; case 0xBF5AF2 -> "Purple"; case 0xFFFFFF -> "White"; case 0xFF6B35 -> "Orange"; default -> String.format("#%06X", color & 0xFFFFFF); }; }
    private static String keyName(int key) { if (key < 0) return "UNBOUND"; String name = GLFW.glfwGetKeyName(key, 0); return name == null ? "KEY " + key : name.toUpperCase(); }
    private static String trim(double value) { String s = String.format(java.util.Locale.ROOT, "%.2f", value); return s.replaceAll("0+$", "").replaceAll("\\.$", ""); }
    private static ZenithConfig.EntityHighlightMode previous(ZenithConfig.EntityHighlightMode value) { ZenithConfig.EntityHighlightMode[] values = ZenithConfig.EntityHighlightMode.values(); return values[Math.floorMod(value.ordinal() - 1, values.length)]; }
    private static ZenithConfig.BlockHighlightMode previous(ZenithConfig.BlockHighlightMode value) { ZenithConfig.BlockHighlightMode[] values = ZenithConfig.BlockHighlightMode.values(); return values[Math.floorMod(value.ordinal() - 1, values.length)]; }
    private static ZenithConfig.EspShape previous(ZenithConfig.EspShape value) { ZenithConfig.EspShape[] values = ZenithConfig.EspShape.values(); return values[Math.floorMod(value.ordinal() - 1, values.length)]; }
    private static ZenithConfig.XrayMode previous(ZenithConfig.XrayMode value) { ZenithConfig.XrayMode[] values = ZenithConfig.XrayMode.values(); return values[Math.floorMod(value.ordinal() - 1, values.length)]; }

    @Override
    public void onClose() { commitNumber(); config.save(); if (minecraft != null) minecraft.setScreenAndShow(parent); }
}
