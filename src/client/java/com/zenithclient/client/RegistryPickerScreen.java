package com.zenithclient.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import com.zenithclient.client.modules.DynamicEntityCatalog;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Searchable multi-select registry list used by ESP, aura, block ESP and X-Ray. */
public final class RegistryPickerScreen extends Screen {
    public enum Mode { ENTITY_ESP, KILL_AURA, BLOCK_ESP, XRAY }
    private enum Action { ENTRY, CLEAR, SELECT_VISIBLE, DONE }
    private record Row(Action action, String id, int x, int y, int w, int h) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private final Screen parent;
    private final ZenithConfig config;
    private final Mode mode;
    private final List<String> entries = new ArrayList<>();
    private final Set<String> liveEntries = new LinkedHashSet<>();
    private final Set<String> selected = new LinkedHashSet<>();
    private final List<Row> rows = new ArrayList<>();
    private String search = "";
    private int scroll;
    private int maxScroll;
    private long nextRefreshMs;

    private RegistryPickerScreen(Screen parent, ZenithConfig config, Mode mode) {
        super(Component.literal("Registry Picker"));
        this.parent = parent;
        this.config = config;
        this.mode = mode;
        refreshEntries(true);
        readSelected();
    }

    public static RegistryPickerScreen of(Screen parent, ZenithConfig config, Mode mode) {
        return new RegistryPickerScreen(parent, config, mode);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float tickDelta) {
        refreshEntries(false);
        int margin = width < 620 || height < 390 ? 7 : 14;
        int panelW = Math.max(1, Math.min(680, width - margin * 2));
        int panelH = Math.max(1, Math.min(480, height - margin * 2));
        int left = Math.max(0, (width - panelW) / 2);
        int top = Math.max(0, (height - panelH) / 2);
        int accent = 0xFFFF5A1F;
        rows.clear();

        g.fill(0, 0, width, height, 0xB0060708);
        g.fill(left, top, left + panelW, top + panelH, 0xF5090A0C);
        frame(g, left, top, panelW, panelH, accent);
        g.fill(left + 1, top + 1, left + panelW - 1, top + 68, 0xFF0E1013);
        g.fill(left + 18, top + 61, left + Math.min(panelW - 18, 158), top + 63, accent);

        g.text(font, titleText().toUpperCase(Locale.ROOT), left + 18, top + 14, 0xFFFFFFFF, true);
        String countText = selected.size() + " SELECTED / " + entries.size()
                + " AVAILABLE / " + liveEntries.size() + " LIVE";
        int countX = Math.max(left + 18, left + panelW - font.width(countText) - 18);
        g.text(font, countText, countX, top + 14, accent, true);

        int searchX = left + 18;
        int searchY = top + 36;
        int searchW = panelW - 36;
        g.fill(searchX, searchY, searchX + searchW, searchY + 20, 0xFF15181D);
        frame(g, searchX, searchY, searchW, 20, search.isEmpty() ? 0xFF343941 : accent);
        String noun = (mode == Mode.ENTITY_ESP || mode == Mode.KILL_AURA) ? "entities" : "blocks";
        String shownSearch = search.isEmpty()
                ? "Search registry, mods and current-world " + noun + "..."
                : search + "_";
        g.text(font, fit(shownSearch, searchW - 16), searchX + 8, searchY + 7,
                search.isEmpty() ? 0xFF737A84 : 0xFFFFFFFF, false);

        int contentTop = top + 76;
        int contentBottom = top + panelH - 48;
        int rowX = left + 18;
        int rowW = panelW - 45;
        List<String> filtered = filtered();
        int rowHeight = 25;
        int viewportH = Math.max(1, contentBottom - contentTop);
        int totalHeight = filtered.size() * rowHeight;
        maxScroll = Math.max(0, totalHeight - viewportH);
        scroll = Math.max(0, Math.min(maxScroll, scroll));

        g.enableScissor(rowX, contentTop, rowX + rowW, contentBottom);
        int first = Math.max(0, scroll / rowHeight);
        int y = contentTop - (scroll % rowHeight);
        for (int i = first; i < filtered.size() && y < contentBottom; i++) {
            drawEntry(g, mouseX, mouseY, filtered.get(i), rowX, y, rowW, accent);
            y += rowHeight;
        }
        if (filtered.isEmpty()) {
            g.text(font, "No loaded registry entries match the search.", rowX + 8,
                    contentTop + 12, 0xFFFFC15A, false);
        }
        g.disableScissor();

        if (maxScroll > 0) {
            int trackX = left + panelW - 18;
            int thumbH = Math.max(28, viewportH * viewportH / Math.max(viewportH, totalHeight));
            int thumbY = contentTop + (viewportH - thumbH) * scroll / maxScroll;
            g.fill(trackX, contentTop, trackX + 3, contentBottom, 0xFF25292F);
            g.fill(trackX, thumbY, trackX + 3, thumbY + thumbH, accent);
        }

        int buttonY = top + panelH - 34;
        int usable = panelW - 36;
        int clearW = Math.min(84, Math.max(60, usable / 5));
        int doneW = Math.min(78, Math.max(62, usable / 5));
        int selectW = Math.max(100, usable - clearW - doneW - 16);
        drawButton(g, mouseX, mouseY, Action.CLEAR, left + 18, buttonY,
                clearW, 21, "CLEAR", accent);
        drawButton(g, mouseX, mouseY, Action.SELECT_VISIBLE, left + 26 + clearW,
                buttonY, selectW, 21, "SELECT SEARCH", accent);
        drawButton(g, mouseX, mouseY, Action.DONE, left + panelW - 18 - doneW,
                buttonY, doneW, 21, "DONE", accent);

        super.extractRenderState(g, mouseX, mouseY, tickDelta);
    }

    private void drawEntry(GuiGraphicsExtractor g, int mx, int my, String id,
                           int x, int y, int w, int accent) {
        boolean checked = selected.contains(id);
        boolean hover = inside(mx, my, x, y, w, 22);
        g.fill(x, y, x + w, y + 22,
                checked ? 0x332FF55A : hover ? 0xFF191C21 : 0xFF111318);
        frame(g, x, y, w, 22, checked ? accent : hover ? 0xFF565D67 : 0xFF2C3036);

        int boxX = x + 7;
        int boxY = y + 5;
        g.fill(boxX, boxY, boxX + 12, boxY + 12, checked ? accent : 0xFF20242A);
        frame(g, boxX, boxY, 12, 12, checked ? 0xFFFFA12B : 0xFF555C66);
        if (checked) {
            g.fill(boxX + 3, boxY + 5, boxX + 5, boxY + 8, 0xFFFFFFFF);
            g.fill(boxX + 5, boxY + 7, boxX + 9, boxY + 9, 0xFFFFFFFF);
        }

        String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        boolean live = liveEntries.contains(id);
        String badge = live ? "LIVE" : badge(path);
        int badgeX = x + 25;
        int badgeW = live ? 30 : 18;
        int badgeColor = live ? 0xFF1D5A35
                : 0xFF000000 | (id.hashCode() & 0x005F5F5F) | 0x00303030;
        g.fill(badgeX, y + 3, badgeX + badgeW, y + 19, badgeColor);
        frame(g, badgeX, y + 3, badgeW, 16, live ? 0xFF39D77A : checked ? accent : 0xFF626873);
        g.text(font, badge, badgeX + (badgeW - font.width(badge)) / 2, y + 7, 0xFFFFFFFF, true);

        String display = fit(id, Math.max(20, w - 57 - badgeW));
        g.text(font, display, badgeX + badgeW + 7, y + 7,
                checked || hover ? 0xFFFFFFFF : 0xFFB9BDC3, false);
        rows.add(new Row(Action.ENTRY, id, x, y, w, 22));
    }

    private void drawButton(GuiGraphicsExtractor g, int mx, int my, Action action,
                            int x, int y, int w, int h, String text, int accent) {
        boolean hover = inside(mx, my, x, y, w, h);
        g.fill(x, y, x + w, y + h, hover ? accent : 0xFF111318);
        frame(g, x, y, w, h, accent);
        g.text(font, text, x + (w - font.width(text)) / 2, y + 7,
                hover ? 0xFF08090B : 0xFFFFFFFF, true);
        rows.add(new Row(action, "", x, y, w, h));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        for (Row row : rows) {
            if (!row.contains(event.x(), event.y())) continue;
            switch (row.action) {
                case ENTRY -> {
                    if (!selected.add(row.id)) selected.remove(row.id);
                    saveSelected();
                }
                case CLEAR -> {
                    selected.clear();
                    saveSelected();
                }
                case SELECT_VISIBLE -> {
                    selected.addAll(filtered());
                    saveSelected();
                }
                case DONE -> onClose();
            }
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
        if (event.key() == GLFW.GLFW_KEY_ESCAPE || event.key() == GLFW.GLFW_KEY_ENTER
                || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
            onClose();
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_BACKSPACE) {
            if (!search.isEmpty()) search = search.substring(0, search.length() - 1);
            scroll = 0;
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_DELETE) {
            search = "";
            scroll = 0;
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent input) {
        int c = input.codepoint();
        if (c >= 32 && c < 127 && search.length() < 80) {
            search += Character.toString((char) c).toLowerCase(Locale.ROOT);
            scroll = 0;
            return true;
        }
        return super.charTyped(input);
    }

    @Override
    public void onClose() {
        saveSelected();
        if (minecraft != null) minecraft.setScreenAndShow(parent);
    }

    private void readSelected() {
        String raw = currentValue();
        if (raw == null || raw.isBlank()) return;
        for (String token : raw.split(",")) {
            String id = token.trim().toLowerCase(Locale.ROOT);
            if (!id.isEmpty() && entries.contains(id)) selected.add(id);
        }
    }

    private void saveSelected() {
        String value = String.join(",", selected);
        switch (mode) {
            case ENTITY_ESP -> config.entitySearch = value;
            case KILL_AURA -> config.killAuraSearch = value;
            case BLOCK_ESP -> config.blockSearch = value;
            case XRAY -> config.xraySearch = value;
        }
        config.save();
        if (mode == Mode.XRAY) ZenithClient.refreshWorldRenderer();
    }

    private String currentValue() {
        return switch (mode) {
            case ENTITY_ESP -> config.entitySearch;
            case KILL_AURA -> config.killAuraSearch;
            case BLOCK_ESP -> config.blockSearch;
            case XRAY -> config.xraySearch;
        };
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
            case ENTITY_ESP -> "Entity ESP Targets";
            case KILL_AURA -> "Kill Aura Targets";
            case BLOCK_ESP -> "Block ESP Targets";
            case XRAY -> "X-Ray Blocks";
        };
    }

    private void refreshEntries(boolean force) {
        long now = System.currentTimeMillis();
        if (!force && now < nextRefreshMs) return;
        nextRefreshMs = now + 1000L;

        if (mode == Mode.ENTITY_ESP || mode == Mode.KILL_AURA) {
            DynamicEntityCatalog.Snapshot snapshot =
                    DynamicEntityCatalog.collect(currentValue());
            entries.clear();
            entries.addAll(snapshot.all());
            liveEntries.clear();
            liveEntries.addAll(snapshot.live());
        } else {
            entries.clear();
            BuiltInRegistries.BLOCK.keySet().forEach(id ->
                    entries.add(id.toString().toLowerCase(Locale.ROOT)));
            entries.sort(Comparator.naturalOrder());
            liveEntries.clear();
        }

        maxScroll = 0;
    }

    private static String badge(String path) {
        String cleaned = path.replace('_', ' ').trim();
        if (cleaned.isEmpty()) return "?";
        String[] words = cleaned.split("\\s+");
        if (words.length >= 2) {
            return ("" + Character.toUpperCase(words[0].charAt(0))
                    + Character.toUpperCase(words[1].charAt(0)));
        }
        return cleaned.substring(0, Math.min(2, cleaned.length())).toUpperCase(Locale.ROOT);
    }

    private String fit(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        String suffix = "...";
        String clipped = text;
        while (!clipped.isEmpty() && font.width(clipped) + font.width(suffix) > maxWidth) {
            clipped = clipped.substring(0, clipped.length() - 1);
        }
        return clipped.isEmpty() ? suffix : clipped + suffix;
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
}
