package com.zenithclient.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class RegistryPickerScreen extends Screen {
    public enum Mode { ENTITY_ESP, KILL_AURA, BLOCK_ESP, XRAY }
    private record Row(String id, int x, int y, int w, int h) { }

    private final Screen parent;
    private final ZenithConfig config;
    private final Mode mode;
    private final List<String> entries;
    private final List<Row> rows = new ArrayList<>();
    private String search = "";
    private int scroll;
    private int maxScroll;

    private RegistryPickerScreen(Screen parent, ZenithConfig config, Mode mode) {
        super(Component.literal("Registry Picker"));
        this.parent = parent;
        this.config = config;
        this.mode = mode;
        this.entries = loadEntries(mode);
    }

    public static RegistryPickerScreen of(Screen parent, ZenithConfig config, Mode mode) {
        return new RegistryPickerScreen(parent, config, mode);
    }

    public void render(GuiGraphicsExtractor g, int mouseX, int mouseY, float tickDelta) {
        int w = Math.min(560, width - 30);
        int h = Math.min(420, height - 30);
        int left = (width - w) / 2;
        int top = (height - h) / 2;
        rows.clear();

        g.fill(0, 0, width, height, 0x88000000);
        g.fill(left, top, left + w, top + h, 0xF0181818);
        g.fill(left, top, left + w, top + 3, 0xFFFF6B35);
        g.text(font, titleText(), left + 14, top + 14, 0xFFFFFFFF, true);
        g.text(font, "Search: " + (search.isEmpty() ? "" : search), left + 14, top + 32, 0xFFCCCCCC, false);
        g.text(font, "Click one to use it. Backspace edits. Esc returns.", left + 14, top + h - 18, 0xFF999999, false);

        int y = top + 56 - scroll;
        drawRow(g, mouseX, mouseY, "__clear__", left + 12, y, w - 24, "Clear filter");
        y += 24;
        int shown = 0;
        for (String id : filtered()) {
            if (y > top + h - 34) break;
            if (y >= top + 52) {
                drawRow(g, mouseX, mouseY, id, left + 12, y, w - 24, id);
                shown++;
            }
            y += 24;
            if (shown > 120) break;
        }
        maxScroll = Math.max(0, y + scroll - (top + h - 30));
    }

    private void drawRow(GuiGraphicsExtractor g, int mouseX, int mouseY, String id, int x, int y, int w, String label) {
        boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 21;
        g.fill(x, y, x + w, y + 21, hover ? 0x44FF6B35 : 0x22000000);
        g.text(font, label, x + 8, y + 6, id.equals("__clear__") ? 0xFFFFC107 : 0xFFE8E8E8, false);
        rows.add(new Row(id, x, y, w, 21));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        for (Row row : rows) {
            if (event.x() < row.x || event.x() >= row.x + row.w || event.y() < row.y || event.y() >= row.y + row.h) continue;
            apply(row.id.equals("__clear__") ? "" : row.id);
            config.save();
            if (mode == Mode.XRAY) ZenithClient.refreshWorldRenderer();
            onClose();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scroll = Math.max(0, Math.min(maxScroll, scroll - (int) Math.round(verticalAmount * 28.0)));
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        if (event.key() == GLFW.GLFW_KEY_BACKSPACE) {
            if (!search.isEmpty()) search = search.substring(0, search.length() - 1);
            scroll = 0;
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
            List<String> matches = filtered();
            if (!matches.isEmpty()) {
                apply(matches.get(0));
                config.save();
                if (mode == Mode.XRAY) ZenithClient.refreshWorldRenderer();
                onClose();
            }
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent input) {
        int c = input.codepoint();
        if (c >= 32 && c < 127) {
            search += Character.toString((char) c).toLowerCase(Locale.ROOT);
            scroll = 0;
            return true;
        }
        return super.charTyped(input);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreenAndShow(parent);
    }

    private void apply(String id) {
        switch (mode) {
            case ENTITY_ESP -> config.entitySearch = id;
            case KILL_AURA -> config.killAuraSearch = id;
            case BLOCK_ESP -> config.blockSearch = id;
            case XRAY -> config.xraySearch = id;
        }
    }

    private List<String> filtered() {
        String q = search.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return entries;
        List<String> matches = new ArrayList<>();
        for (String id : entries) if (id.contains(q)) matches.add(id);
        return matches;
    }

    private String titleText() {
        return switch (mode) {
            case ENTITY_ESP -> "Choose Entity ESP Target";
            case KILL_AURA -> "Choose Kill Aura Target";
            case BLOCK_ESP -> "Choose Block ESP Target";
            case XRAY -> "Choose X-Ray Block";
        };
    }

    private static List<String> loadEntries(Mode mode) {
        List<String> values = new ArrayList<>();
        if (mode == Mode.ENTITY_ESP || mode == Mode.KILL_AURA) {
            BuiltInRegistries.ENTITY_TYPE.keySet().forEach(id -> values.add(id.toString().toLowerCase(Locale.ROOT)));
        } else {
            BuiltInRegistries.BLOCK.keySet().forEach(id -> values.add(id.toString().toLowerCase(Locale.ROOT)));
        }
        values.sort(Comparator.naturalOrder());
        return values;
    }
}
