package de.flori.skycustomizer.config;

import com.google.gson.Gson;
import de.flori.skycustomizer.util.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;

import static de.flori.skycustomizer.render.ModernRenderHelper.*;

public final class TooltipCanvasScreen extends StudioScreen {
    private static final Gson GSON = new Gson();
    private final Screen parent;
    private final Consumer<SkyCustomizerConfig.ItemConfig> apply;
    private SkyCustomizerConfig.ItemConfig staged;
    private boolean dragging;
    private float grabX, grabY;

    public TooltipCanvasScreen(Screen parent, SkyCustomizerConfig.ItemConfig style,
                               Consumer<SkyCustomizerConfig.ItemConfig> apply) {
        super("Tooltip Canvas");
        this.parent = parent;
        this.apply = apply;
        staged = copy(style);
    }

    @Override protected String[] historyFields() { return new String[]{"staged"}; }
    @Override protected void afterHistory() {}

    @Override protected void init() { clearWidgets(); startHistory(); }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        backdrop(graphics, delta);
        fillRect(graphics, 0, 0, width, height, 0x98080604);
        int panelWidth = Math.min(520, width - 16);
        int panelX = (width - panelWidth) / 2;
        fillRoundRect(graphics, panelX, 8, panelWidth, 45, 7, 0xF018130F);
        drawRoundBorder(graphics, panelX, 8, panelWidth, 45, 7, C_BORDER);
        drawString(graphics, "Tooltip Canvas", panelX + 13, 17, C_TEXT);
        drawString(graphics, staged.tooltipCanvasPinned ? "PINNED" : "FOLLOWS CURSOR", panelX + panelWidth - 118, 17, C_GOLD);
        button(graphics, new Rect(panelX + 12, 31, 112, 17), staged.tooltipCanvasPinned ? "Unpin" : "Pin tooltip", staged.tooltipCanvasPinned, mouseX, mouseY);
        button(graphics, new Rect(panelX + 129, 31, 92, 17), "Reset", false, mouseX, mouseY);
        button(graphics, new Rect(panelX + panelWidth - 132, 31, 56, 17), "Back", false, mouseX, mouseY);
        button(graphics, new Rect(panelX + panelWidth - 70, 31, 58, 17), "Apply", true, mouseX, mouseY);

        Rect tooltip = tooltipBounds();
        fillRoundRect(graphics, tooltip.x(), tooltip.y(), tooltip.w(), tooltip.h(), 4, 0xF0100010);
        drawRoundBorder(graphics, tooltip.x(), tooltip.y(), tooltip.w(), tooltip.h(), 4, C_GOLD);
        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate(tooltip.x(), tooltip.y());
            graphics.pose().scale(staged.tooltipCanvasScale, staged.tooltipCanvasScale);
            int accent = ColorUtil.fromHex(staged.tooltipAccentHex);
            graphics.fill(4, 4, tooltipBaseWidth() - 4, 5, 0xFF000000 | accent);
            int y = 10;
            for (Component line : previewLines()) {
                graphics.text(font, line, 6, y, 0xFFFFFFFF, false);
                y += 11;
            }
        } finally {
            graphics.pose().popMatrix();
        }
        int labelY = Math.max(57, tooltip.y() - 16);
        fillRoundRect(graphics, tooltip.x(), labelY, Math.min(tooltip.w(), 155), 13, 3, 0xF0302219);
        drawString(graphics, "DRAG · WHEEL: SCALE", tooltip.x() + 5, labelY + 3, C_GOLD);

        int footerY = height - 45;
        fillRoundRect(graphics, 8, footerY, width - 16, 37, 6, 0xF018130F);
        drawRoundBorder(graphics, 8, footerY, width - 16, 37, 6, C_BORDER);
        String location = String.format(java.util.Locale.ROOT, "%.2fx   X:%d   Y:%d", staged.tooltipCanvasScale,
                staged.tooltipCanvasPinned ? staged.tooltipCanvasPinnedX : staged.tooltipCanvasOffsetX,
                staged.tooltipCanvasPinned ? staged.tooltipCanvasPinnedY : staged.tooltipCanvasOffsetY);
        drawString(graphics, location, 18, footerY + 10, C_TEXT);
        drawString(graphics, "Dragging pins the tooltip · P: pin · R: reset", 18, footerY + 24, C_TEXT_DIM);
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event == null || event.button() != 0) return false;
        int panelWidth = Math.min(520, width - 16);
        int panelX = (width - panelWidth) / 2;
        double x = event.x(), y = event.y();
        if (new Rect(panelX + 12, 31, 112, 17).hit(x, y)) { staged.tooltipCanvasPinned = !staged.tooltipCanvasPinned; checkpoint(); return true; }
        if (new Rect(panelX + 129, 31, 92, 17).hit(x, y)) { reset(); return true; }
        if (new Rect(panelX + panelWidth - 132, 31, 56, 17).hit(x, y)) { onClose(); return true; }
        if (new Rect(panelX + panelWidth - 70, 31, 58, 17).hit(x, y)) { save(); return true; }
        Rect tooltip = tooltipBounds();
        if (tooltip.hit(x, y)) {
            dragging = true;
            grabX = (float) x - tooltip.x();
            grabY = (float) y - tooltip.y();
            return true;
        }
        return false;
    }

    @Override public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (event == null || event.button() != 0 || !dragging) return false;
        float nextX = (float) event.x() - grabX;
        float nextY = (float) event.y() - grabY;
        int maxX = Math.max(0, width - tooltipBounds().w());
        int maxY = Math.max(56, height - 54 - tooltipBounds().h());
        staged.tooltipCanvasPinned = true;
        staged.tooltipCanvasPinnedX = Math.clamp(Math.round(nextX), 0, maxX);
        staged.tooltipCanvasPinnedY = Math.clamp(Math.round(nextY), 56, maxY);
        checkpoint();
        return true;
    }

    @Override public boolean mouseReleased(MouseButtonEvent event) {
        if (event == null || event.button() != 0) return false;
        boolean handled = dragging;
        dragging = false;
        return handled;
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (!tooltipBounds().hit(mouseX, mouseY)) return false;
        staged.tooltipCanvasScale = Math.clamp(staged.tooltipCanvasScale + (float) vertical * .05f, .5f, 2f);
        checkpoint();
        return true;
    }

    @Override public boolean keyPressed(KeyEvent event) {
        if (event == null) return false;
        if (event.key() == 256) { onClose(); return true; }
        if (event.key() == 80) { staged.tooltipCanvasPinned = !staged.tooltipCanvasPinned; checkpoint(); return true; }
        if (event.key() == 82) { reset(); return true; }
        int step = (event.modifiers() & 1) != 0 ? 8 : 1;
        int x = event.key() == 263 ? -step : event.key() == 262 ? step : 0;
        int y = event.key() == 265 ? -step : event.key() == 264 ? step : 0;
        if (x != 0 || y != 0) {
            staged.tooltipCanvasPinned = true;
            staged.tooltipCanvasPinnedX = Math.clamp(staged.tooltipCanvasPinnedX + x, 0, Math.max(0, width - tooltipBounds().w()));
            staged.tooltipCanvasPinnedY = Math.clamp(staged.tooltipCanvasPinnedY + y, 56, Math.max(56, height - 54 - tooltipBounds().h()));
            checkpoint();
            return true;
        }
        return super.keyPressed(event);
    }

    private Rect tooltipBounds() {
        float scale = Math.clamp(staged.tooltipCanvasScale, .5f, 2f);
        int w = Math.round(tooltipBaseWidth() * scale);
        int h = Math.round(tooltipBaseHeight() * scale);
        int x = staged.tooltipCanvasPinned ? staged.tooltipCanvasPinnedX : width / 2 + staged.tooltipCanvasOffsetX;
        int y = staged.tooltipCanvasPinned ? staged.tooltipCanvasPinnedY : height / 2 + staged.tooltipCanvasOffsetY;
        return new Rect(Math.clamp(x, 0, Math.max(0, width - w)), Math.clamp(y, 56, Math.max(56, height - 54 - h)), w, h);
    }

    private int tooltipBaseWidth() { return previewLines().stream().mapToInt(font::width).max().orElse(120) + 12; }
    private int tooltipBaseHeight() { return 13 + previewLines().size() * 11; }
    private List<Component> previewLines() {
        return List.of(Component.literal("Ember Blade").withColor(0xFFE2944E),
                Component.literal("LEGENDARY SWORD").withColor(0xFFFFAA00),
                Component.literal("Damage: +320").withColor(0xFFAAAAAA),
                Component.literal("Forged for exploration.").withColor(0xFF55FFFF));
    }
    private void reset() {
        staged.tooltipCanvasPinned = false;
        staged.tooltipCanvasOffsetX = 0;
        staged.tooltipCanvasOffsetY = 0;
        staged.tooltipCanvasPinnedX = 24;
        staged.tooltipCanvasPinnedY = 24;
        staged.tooltipCanvasScale = 1;
        checkpoint();
    }
    private void save() {
        staged.tooltipCanvasScale = Math.clamp(staged.tooltipCanvasScale, .5f, 2f);
        apply.accept(copy(staged));
        onClose();
    }
    private static SkyCustomizerConfig.ItemConfig copy(SkyCustomizerConfig.ItemConfig source) {
        return GSON.fromJson(GSON.toJson(source), SkyCustomizerConfig.ItemConfig.class);
    }
    @Override public void onClose() { dragging = false; Minecraft.getInstance().setScreen(parent); }
    private void button(Object graphics, Rect rect, String label, boolean active, int mouseX, int mouseY) {
        boolean hover = rect.hit(mouseX, mouseY);
        fillRoundRect(graphics, rect.x(), rect.y(), rect.w(), rect.h(), 4, hover ? C_HOVER : active ? C_ACTIVE : C_SURFACE);
        drawRoundBorder(graphics, rect.x(), rect.y(), rect.w(), rect.h(), 4, active || hover ? C_GOLD_DARK : C_BORDER);
        drawCenteredString(graphics, label, rect.x() + rect.w() / 2, rect.y() + 5, active || hover ? C_GOLD : C_TEXT_DIM);
    }
    private record Rect(int x, int y, int w, int h) {
        boolean hit(double px, double py) { return px >= x && px < x + w && py >= y && py < y + h; }
    }
}
