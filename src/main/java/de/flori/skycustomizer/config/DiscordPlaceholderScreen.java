package de.flori.skycustomizer.config;

import de.flori.skycustomizer.discord.DiscordPlaceholders;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import static de.flori.skycustomizer.render.ModernRenderHelper.*;
import static de.flori.skycustomizer.config.StudioWidgets.*;

public final class DiscordPlaceholderScreen extends StudioScreen {
    private final Screen parent;
    private final SkyCustomizerConfig.DiscordConfig settings;
    private final Consumer<String> insert;
    private List<DiscordPlaceholders.Entry> matches = DiscordPlaceholders.ALL;
    private EditBox search;
    private String query = "";
    private int x, y, w, h, scroll, selected;

    public DiscordPlaceholderScreen(Screen parent, SkyCustomizerConfig.DiscordConfig settings, Consumer<String> insert) {
        super("Discord Placeholders");
        this.parent = parent;
        this.settings = settings;
        this.insert = insert;
    }

    @Override protected void init() {
        clearWidgets();
        w = Math.min(620, width - 16); h = Math.min(470, height - 16);
        x = (width - w) / 2; y = (height - h) / 2;
        search = edit("Search placeholders", query, 80);
        search.setHint(Component.literal("Search item, armor, position..."));
        search.setResponder(value -> {
            query = value;
            String term = value.toLowerCase(Locale.ROOT).trim();
            matches = DiscordPlaceholders.ALL.stream().filter(entry ->
                    (entry.token() + " " + entry.description() + " " + entry.privacy()).toLowerCase(Locale.ROOT).contains(term)).toList();
            scroll = 0; selected = 0;
        });
        addRenderableWidget(search);
        setInitialFocus(search);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        backdrop(g, delta);
        fillRect(g, 0, 0, width, height, 0xAD080604);
        fillRoundRect(g, x, y, w, h, 10, C_PANEL);
        drawRoundBorder(g, x, y, w, h, 10, C_BORDER);
        drawString(g, font.plainSubstrByWidth("Discord Placeholders", w - 110), x + 16, y + 14, C_TEXT);
        button(g, "Back", x + w - 75, y + 7, 59, mx, my, false);
        field(g, search, x + 14, y + 39, w - 28, mx, my, delta);
        int top = y + 74, bottom = y + h - 34;
        scroll = Math.clamp(scroll, 0, Math.max(0, matches.size() * 66 - (bottom - top)));
        g.enableScissor(x + 10, top, x + w - 10, bottom);
        DiscordPlaceholders.Entry hovered = null;
        for (int i = 0; i < matches.size(); i++) {
            int cy = top + i * 66 - scroll;
            if (cy + 61 <= top || cy >= bottom) continue;
            var entry = matches.get(i);
            boolean hover = hit(mx, my, x + 14, cy, w - 28, 61) && my >= top && my < bottom;
            fillRoundRect(g, x + 14, cy, w - 28, 61, 6, hover ? C_HOVER : C_SURFACE);
            drawRoundBorder(g, x + 14, cy, w - 28, 61, 6, i == selected ? C_GOLD_DARK : C_BORDER);
            drawString(g, entry.token(), x + 24, cy + 8, C_GOLD);
            String permission = entry.privacy().allowed(settings) ? "+" : "OFF";
            drawString(g, permission, x + w - 50, cy + 8, C_TEXT_DIM);
            drawString(g, font.plainSubstrByWidth(entry.description(), w - 48), x + 24, cy + 25, C_TEXT);
            drawString(g, font.plainSubstrByWidth("Example: " + entry.example(), w - 48), x + 24, cy + 42, C_TEXT_DIM);
            if (hover) hovered = entry;
        }
        if (matches.isEmpty()) drawString(g, "No matching placeholders", x + 24, top + 15, C_TEXT_DIM);
        g.disableScissor();
        drawString(g, font.plainSubstrByWidth("Click / Enter to insert. OFF = hidden by Privacy.", w - 28), x + 14, y + h - 22, C_TEXT_DIM);
        if (hovered != null) g.setComponentTooltipForNextFrame(font, List.of(Component.literal(hovered.description()),
                Component.literal("Privacy: " + hovered.privacy().name().toLowerCase(Locale.ROOT))), mx, my);
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean twice) {
        if (event.button() == 0) {
            if (hit(event.x(), event.y(), x + w - 75, y + 7, 59, 24)) { onClose(); return true; }
            if (hit(event.x(), event.y(), x + 14, y + 74, w - 28, h - 108)) {
                int index = (int) (event.y() - y - 74 + scroll) / 66;
                if (index >= 0 && index < matches.size()) { selected = index; choose(); return true; }
            }
        }
        return super.mouseClicked(event, twice);
    }

    @Override public boolean keyPressed(KeyEvent event) {
        if ((event.key() == 264 || event.key() == 265) && !matches.isEmpty()) {
            selected = Math.clamp(selected + (event.key() == 264 ? 1 : -1), 0, matches.size() - 1);
            int viewport = h - 108;
            if (selected * 66 < scroll) scroll = selected * 66;
            if (selected * 66 + 61 > scroll + viewport) scroll = selected * 66 + 61 - viewport;
            return true;
        }
        if (event.key() == 257 || event.key() == 335) { choose(); return true; }
        return super.keyPressed(event);
    }

    private void choose() {
        if (matches.isEmpty()) return;
        insert.accept(matches.get(selected).token());
        onClose();
    }

    @Override public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (!hit(mx, my, x + 10, y + 74, w - 20, h - 108)) return false;
        scroll = Math.clamp(scroll - (int) (vertical * 30), 0, Math.max(0, matches.size() * 66 - (h - 108)));
        return true;
    }

    @Override public void onClose() { minecraft.setScreen(parent); }
}
