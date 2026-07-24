package com.zenithclient.client;

import com.zenithclient.client.modules.CombatUtilityState;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Settings for Breach Swap and EXP Thrower. */
public final class CombatUtilitySettingsScreen extends Screen {
    public enum Type { BREACH_SWAP, EXP_THROWER }

    private enum Action { ENABLED, OPTION, KEYBIND, BACK }

    private record Hit(Action action, int index, int x, int y, int w, int h) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private final Screen parent;
    private final Type type;
    private final List<Hit> hits = new ArrayList<>();
    private boolean capturingKey;

    private CombatUtilitySettingsScreen(Screen parent, Type type) {
        super(Component.literal(type == Type.BREACH_SWAP
                ? "Breach Swap Settings"
                : "EXP Thrower Settings"));
        this.parent = parent;
        this.type = type;
    }

    public static CombatUtilitySettingsScreen of(Screen parent, Type type) {
        return new CombatUtilitySettingsScreen(parent, type);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        int panelW = Math.min(500, width - 28);
        int panelH = Math.min(330, height - 24);
        int left = (width - panelW) / 2;
        int top = (height - panelH) / 2;
        int accent = 0xFFFF5A1F;

        hits.clear();
        g.fill(0, 0, width, height, 0xB0060708);
        g.fill(left, top, left + panelW, top + panelH, 0xF20B0C0E);
        frame(g, left, top, panelW, panelH, accent);

        g.text(font, titleText(), left + 18, top + 16, 0xFFFFFFFF, true);
        g.text(font, descriptionText(), left + 18, top + 33, 0xFF969CA5, false);
        g.fill(left + 18, top + 54, left + 128, top + 56, accent);

        int x = left + 18;
        int y = top + 72;
        int w = panelW - 36;

        row(g, mouseX, mouseY, Action.ENABLED, 0, x, y, w,
                "Enabled", enabled() ? "ON" : "OFF", accent);
        y += 42;

        if (type == Type.BREACH_SWAP) {
            row(g, mouseX, mouseY, Action.OPTION, 0, x, y, w,
                    "Only armored targets",
                    CombatUtilityState.breachOnlyArmored() ? "ON" : "OFF", accent);
            y += 42;
            row(g, mouseX, mouseY, Action.OPTION, 1, x, y, w,
                    "Restore delay",
                    CombatUtilityState.breachRestoreDelay() + " ticks", accent);
            y += 42;
        } else {
            row(g, mouseX, mouseY, Action.OPTION, 0, x, y, w,
                    "Throw delay",
                    CombatUtilityState.expDelayTicks() + " ticks", accent);
            y += 42;
            row(g, mouseX, mouseY, Action.OPTION, 1, x, y, w,
                    "Look down",
                    CombatUtilityState.expLookDown() ? "ON" : "OFF", accent);
            y += 42;
            row(g, mouseX, mouseY, Action.OPTION, 2, x, y, w,
                    "Swap back",
                    CombatUtilityState.expSwapBack() ? "ON" : "OFF", accent);
            y += 42;
        }

        row(g, mouseX, mouseY, Action.KEYBIND, 0, x, y, w,
                "Keybind",
                capturingKey ? "PRESS A KEY..." : keyName(keybind()), accent);

        int bx = left + panelW - 96;
        int by = top + panelH - 31;
        boolean hover = inside(mouseX, mouseY, bx, by, 78, 21);
        g.fill(bx, by, bx + 78, by + 21, hover ? accent : 0xFF111318);
        frame(g, bx, by, 78, 21, accent);
        g.text(font, "BACK", bx + (78 - font.width("BACK")) / 2, by + 7,
                hover ? 0xFF08090B : 0xFFFFFFFF, true);
        hits.add(new Hit(Action.BACK, 0, bx, by, 78, 21));

        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    private void row(GuiGraphicsExtractor g, int mx, int my, Action action, int index,
                     int x, int y, int w, String label, String value, int accent) {
        boolean hover = inside(mx, my, x, y, w, 34);
        g.fill(x, y, x + w, y + 34, hover ? 0xFF1B1E23 : 0xFF121418);
        frame(g, x, y, w, 34, hover ? accent : 0xFF343941);
        g.fill(x, y, x + 3, y + 34, accent);
        g.text(font, label, x + 12, y + 9, 0xFFF1F2F3, true);
        g.text(font, value, x + w - font.width(value) - 12, y + 9,
                hover ? 0xFFFFFFFF : accent, false);
        hits.add(new Hit(action, index, x, y, w, 34));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        for (Hit hit : hits) {
            if (!hit.contains(event.x(), event.y())) continue;

            if (hit.action == Action.BACK) {
                onClose();
                return true;
            }

            if (hit.action == Action.KEYBIND) {
                capturingKey = true;
                return true;
            }

            if (hit.action == Action.ENABLED) {
                if (type == Type.BREACH_SWAP) CombatUtilityState.toggleBreachSwap();
                else CombatUtilityState.toggleExpThrower();
                return true;
            }

            int direction = event.buttonInfo().button() == 1 ? -1 : 1;
            changeOption(hit.index, direction);
            return true;
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (capturingKey) {
            int key = event.key();
            int selected = key == GLFW.GLFW_KEY_ESCAPE
                    || key == GLFW.GLFW_KEY_BACKSPACE
                    || key == GLFW.GLFW_KEY_DELETE ? -1 : key;

            if (type == Type.BREACH_SWAP) CombatUtilityState.setBreachKey(selected);
            else CombatUtilityState.setExpKey(selected);

            capturingKey = false;
            return true;
        }

        return super.keyPressed(event);
    }

    private void changeOption(int index, int direction) {
        if (type == Type.BREACH_SWAP) {
            if (index == 0) {
                CombatUtilityState.setBreachOnlyArmored(
                        !CombatUtilityState.breachOnlyArmored());
            } else if (index == 1) {
                CombatUtilityState.setBreachRestoreDelay(
                        CombatUtilityState.breachRestoreDelay() + direction);
            }
            return;
        }

        if (index == 0) {
            CombatUtilityState.setExpDelayTicks(
                    CombatUtilityState.expDelayTicks() + direction);
        } else if (index == 1) {
            CombatUtilityState.setExpLookDown(!CombatUtilityState.expLookDown());
        } else if (index == 2) {
            CombatUtilityState.setExpSwapBack(!CombatUtilityState.expSwapBack());
        }
    }

    private boolean enabled() {
        return type == Type.BREACH_SWAP
                ? CombatUtilityState.breachSwapEnabled()
                : CombatUtilityState.expThrowerEnabled();
    }

    private int keybind() {
        return type == Type.BREACH_SWAP
                ? CombatUtilityState.breachKey()
                : CombatUtilityState.expKey();
    }

    private String titleText() {
        return type == Type.BREACH_SWAP ? "BREACH SWAP" : "EXP THROWER";
    }

    private String descriptionText() {
        return type == Type.BREACH_SWAP
                ? "Selects a Breach mace for the normal attack, then restores your slot."
                : "Uses XP bottles from the offhand or hotbar at a configurable rate.";
    }

    private static String keyName(int key) {
        if (key < 0) return "UNBOUND";
        String name = GLFW.glfwGetKeyName(key, 0);
        return name == null ? "KEY " + key : name.toUpperCase();
    }

    private static void frame(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreenAndShow(parent);
    }
}
