package de.flori.skycustomizer.features.visuals;

import de.flori.skycustomizer.config.HudEditorScreen;
import de.flori.skycustomizer.config.SkyCustomizerConfig;
import de.flori.skycustomizer.util.HudLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ActionbarComposer {
    private static final Pattern HEALTH = Pattern.compile("(?i)([0-9][0-9,./]*)\\s*(?:HP|Health|❤)");
    private static final Pattern DEFENSE = Pattern.compile("(?i)([0-9][0-9,./]*)\\s*(?:Defense|❈)");
    private static final Pattern MANA = Pattern.compile("(?i)([0-9][0-9,./]*)\\s*(?:Mana|✎)");

    public record Layout(List<Component> cards, int width, int height) {}

    private ActionbarComposer() {}

    public static boolean enabled() {
        var settings = SkyCustomizerConfig.get().visuals;
        return SkyCustomizerConfig.get().enabled && settings.enabled && settings.customHud && settings.actionbarCustom
                && settings.actionbarComposerEnabled;
    }

    public static Layout layout(Component message) {
        var settings = SkyCustomizerConfig.get().visuals;
        Font font = Minecraft.getInstance().font;
        String text = message == null ? "" : message.getString();
        List<Component> cards = new ArrayList<>(3);
        add(cards, HEALTH, text, "HP", 0xFFFF5555, settings.actionbarComposerShowHealth);
        add(cards, DEFENSE, text, "DEF", 0xFF55FF55, settings.actionbarComposerShowDefense);
        add(cards, MANA, text, "MANA", 0xFF55FFFF, settings.actionbarComposerShowMana);
        if (cards.isEmpty() && !text.isBlank()) cards.add(Component.literal(text).withColor(0xFFFFFFFF));
        int gap = Math.clamp(settings.actionbarComposerGap, 0, 12);
        int width = cards.stream().mapToInt(font::width).sum() + cards.size() * 10 + Math.max(0, cards.size() - 1) * gap;
        return new Layout(cards, Math.max(1, width), cards.isEmpty() ? 0 : 18);
    }

    public static void draw(GuiGraphicsExtractor graphics) {
        if (!enabled() || Minecraft.getInstance().screen instanceof HudEditorScreen) return;
        Component message = HudCustomizer.previewMessage();
        Layout layout = layout(message);
        if (layout.cards().isEmpty()) return;
        var settings = SkyCustomizerConfig.get().visuals;
        HudLayout.Bounds bounds = HudLayout.actionbar(graphics.guiWidth(), graphics.guiHeight(), layout.width(), layout.height(),
                settings.hudScale, settings.hudOffsetX, settings.hudOffsetY);
        drawAt(graphics, layout, bounds.x(), bounds.y(), bounds.scale());
    }

    public static void drawPreview(GuiGraphicsExtractor graphics, Component message, float x, float y, float scale) {
        drawAt(graphics, layout(message), x, y, scale);
    }

    private static void drawAt(GuiGraphicsExtractor graphics, Layout layout, float x, float y, float scale) {
        if (layout.cards().isEmpty()) return;
        var settings = SkyCustomizerConfig.get().visuals;
        Font font = Minecraft.getInstance().font;
        int gap = Math.clamp(settings.actionbarComposerGap, 0, 12);
        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate(x, y);
            graphics.pose().scale(scale, scale);
            int cursor = 0;
            for (Component card : layout.cards()) {
                int width = font.width(card) + 10;
                if (settings.actionbarComposerBackground) {
                    int alpha = Math.clamp(settings.actionbarComposerBackgroundOpacity, 0, 255);
                    graphics.fill(cursor, 0, cursor + width, 18, (alpha << 24) | 0x120D09);
                    graphics.outline(cursor, 0, width, 18, 0xAA6D4B2C);
                }
                graphics.text(font, card, cursor + 5, 5, 0xFFFFFFFF, false);
                cursor += width + gap;
            }
        } finally {
            graphics.pose().popMatrix();
        }
    }

    private static void add(List<Component> cards, Pattern pattern, String text, String label, int color, boolean enabled) {
        if (!enabled) return;
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) cards.add(Component.literal(label + " " + matcher.group(1)).withColor(color));
    }
}
