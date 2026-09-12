package de.flori.skycustomizer.config;

import de.flori.skycustomizer.features.ScoreboardCustomizer;
import de.flori.skycustomizer.features.ScoreboardCustomizer.SidebarSize;
import de.flori.skycustomizer.features.ScoreboardCustomizer.SidebarSnapshot;
import de.flori.skycustomizer.features.visuals.HudCustomizer;
import de.flori.skycustomizer.features.visuals.ActionbarComposer;
import de.flori.skycustomizer.util.HudLayout;
import de.flori.skycustomizer.util.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.Locale;

import static de.flori.skycustomizer.render.ModernRenderHelper.*;

public final class HudEditorScreen extends StudioScreen {
    private static final int HANDLE_SIZE = 11;
    private static final long REFRESH_NANOS = 250_000_000L;
    private static final int GRID_SIZE = 8;

    private enum Target { SCOREBOARD, ACTIONBAR, PERFORMANCE }
    private enum Drag { NONE, MOVE, RESIZE }

    private final Screen parent;
    private final SkyCustomizerConfig config;
    private SkyCustomizerConfig.ScoreboardConfig scoreboard;
    private float actionbarScale;
    private int actionbarOffsetX;
    private int actionbarOffsetY;
    private float performanceScale;
    private int performanceOffsetX;
    private int performanceOffsetY;
    private Target selected = Target.SCOREBOARD;
    private Drag drag = Drag.NONE;
    private boolean grid = true;
    private boolean dirty;
    private float grabX;
    private float grabY;
    private float resizeLeft;
    private float resizeTop;
    private SidebarSnapshot snapshot;
    private SidebarSize sidebarSize;
    private Component actionbarMessage = Component.empty();
    private int actionbarWidth = 1;
    private int actionbarHeight = 9;
    private long refreshedAt;

    public HudEditorScreen(Screen parent) {
        super("HUD Editor");
        this.parent = parent;
        config = SkyCustomizerConfig.get();
        scoreboard = ScoreboardStyles.copy(config.scoreboard);
        actionbarScale = config.visuals.hudScale;
        actionbarOffsetX = config.visuals.hudOffsetX;
        actionbarOffsetY = config.visuals.hudOffsetY;
        performanceScale = config.visuals.performanceHudScale;
        performanceOffsetX = config.visuals.performanceHudOffsetX;
        performanceOffsetY = config.visuals.performanceHudOffsetY;
    }

    @Override protected String[] historyFields() { return new String[]{"scoreboard","actionbarScale","actionbarOffsetX","actionbarOffsetY","performanceScale","performanceOffsetX","performanceOffsetY"}; }
    @Override protected void afterHistory() { dirty=true; refreshPreview(true); }

    @Override
    protected void init() {
        clearWidgets();
        refreshPreview(true);
    }

    private void refreshPreview(boolean force) {
        long now = System.nanoTime();
        if (!force && (drag != Drag.NONE || now - refreshedAt < REFRESH_NANOS)) return;
        snapshot = ScoreboardCustomizer.snapshot(scoreboard);
        sidebarSize = ScoreboardCustomizer.measure(snapshot, scoreboard);
        actionbarMessage = HudCustomizer.previewMessage();
        var actionbarLayout = ActionbarComposer.layout(actionbarMessage);
        actionbarWidth = ActionbarComposer.enabled() ? actionbarLayout.width() : Math.max(1, Minecraft.getInstance().font.width(actionbarMessage));
        actionbarHeight = ActionbarComposer.enabled() ? actionbarLayout.height() : 9;
        refreshedAt = now;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {

        refreshPreview(false);
        fillRect(graphics, 0, 0, width, height, 0x650E0B08);
        if (grid) drawGrid(graphics);

        HudLayout.Bounds boardBounds = bounds(Target.SCOREBOARD);
        ScoreboardCustomizer.draw(graphics, snapshot, scoreboard,
                boardBounds.x(), boardBounds.y(), boardBounds.scale());
        HudLayout.Bounds barBounds = bounds(Target.ACTIONBAR);
        if (ActionbarComposer.enabled()) ActionbarComposer.drawPreview(graphics, actionbarMessage,
                barBounds.x(), barBounds.y(), barBounds.scale());
        else HudCustomizer.drawPreview(graphics, actionbarMessage,
                barBounds.x(), barBounds.y(), barBounds.scale());
        HudLayout.Bounds performanceBounds = bounds(Target.PERFORMANCE);
        drawPerformancePreview(graphics, performanceBounds);

        drawOutline(graphics, Target.SCOREBOARD, boardBounds, mouseX, mouseY);
        drawOutline(graphics, Target.ACTIONBAR, barBounds, mouseX, mouseY);
        drawOutline(graphics, Target.PERFORMANCE, performanceBounds, mouseX, mouseY);
        if (drag != Drag.NONE) drawGuides(graphics, bounds(selected));
        drawToolbar(graphics, mouseX, mouseY);
        drawFooter(graphics, mouseX, mouseY);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {

        if (Minecraft.getInstance().level == null) extractPanorama(graphics, delta);
    }

    private void drawPerformancePreview(GuiGraphicsExtractor graphics, HudLayout.Bounds bounds) {
        var visuals = config.visuals;
        int width = performanceWidth();
        int height = performanceHeight();
        int color = 0xFF000000 | (ColorUtil.fromHex(visuals.performanceHudColorHex) & 0xFFFFFF);
        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate(bounds.x(), bounds.y());
            graphics.pose().scale(bounds.scale(), bounds.scale());
            if (visuals.performanceHudBackground) {
                int alpha = Math.clamp(visuals.performanceHudBackgroundOpacity, 0, 255);
                graphics.fill(0, 0, width, height, (alpha << 24) | 0x130D09);
            }
            graphics.text(Minecraft.getInstance().font, Component.literal(performanceText()), 6, 5, color, false);
        } finally {
            graphics.pose().popMatrix();
        }
    }

    private void drawGrid(Object graphics) {
        for (int x = GRID_SIZE; x < width; x += GRID_SIZE) {
            fillRect(graphics, x, 0, 1, height, 0x10D7C6AF);
        }
        for (int y = GRID_SIZE; y < height; y += GRID_SIZE) {
            fillRect(graphics, 0, y, width, 1, 0x10D7C6AF);
        }
        fillRect(graphics, width / 2, 0, 1, height, 0x24E7B875);
        fillRect(graphics, 0, height / 2, width, 1, 0x24E7B875);
    }

    private void drawGuides(Object graphics, HudLayout.Bounds b) {
        int color = 0xBFFFBC72;
        if (Math.abs(b.x() + b.width() / 2 - width / 2.0f) < 3) {
            fillRect(graphics, width / 2, 0, 1, height, color);
        }
        if (Math.abs(b.y() + b.height() / 2 - height / 2.0f) < 3) {
            fillRect(graphics, 0, height / 2, width, 1, color);
        }
        if (b.x() < 5) fillRect(graphics, Math.round(b.x()), 0, 1, height, color);
        if (b.x() + b.width() > width - 5) {
            fillRect(graphics, Math.min(width - 1, Math.round(b.x() + b.width())), 0, 1, height, color);
        }
    }

    private void drawOutline(Object graphics, Target target, HudLayout.Bounds b, int mx, int my) {
        boolean active = target == selected;
        boolean hovered = hit(b, mx, my);
        int color = active ? C_GOLD : hovered ? 0xC0F6D6AF : 0x607D7367;
        drawBorder(graphics, Math.round(b.x()) - 2, Math.round(b.y()) - 2,
                Math.round(b.width()) + 4, Math.round(b.height()) + 4, color);
        if (!active) return;

        Rect handle = handle(b);
        fillRoundRect(graphics, handle.x(), handle.y(), handle.w(), handle.h(), 2,
                handle.hit(mx, my) || drag == Drag.RESIZE ? C_GOLD : C_ACTIVE);
        drawBorder(graphics, handle.x(), handle.y(), handle.w(), handle.h(), C_GOLD);
        fillRect(graphics, handle.x() + 3, handle.y() + 6, 4, 1, C_TEXT);
        fillRect(graphics, handle.x() + 6, handle.y() + 3, 1, 4, C_TEXT);

        String label = switch (target) {
            case SCOREBOARD -> "SCOREBOARD";
            case ACTIONBAR -> "ACTION BAR";
            case PERFORMANCE -> "FPS · PING · TPS";
        };
        int tagY = Math.max(0, Math.round(b.y()) - 14);
        int tagX = Math.max(0, Math.min(width - getStringWidth(label) - 10, Math.round(b.x()) - 2));
        fillRoundRect(graphics, tagX, tagY, getStringWidth(label) + 10, 12, 3, 0xEA302219);
        drawString(graphics, label, tagX + 5, tagY + 2, C_GOLD);
    }

    private void drawToolbar(Object graphics, int mx, int my) {
        Rect panel = toolbar();
        fillRoundRect(graphics, panel.x(), panel.y(), panel.w(), panel.h(), 6, 0xF018130F);
        drawRoundBorder(graphics, panel.x(), panel.y(), panel.w(), panel.h(), 6, C_BORDER);
        drawMoveIcon(graphics, panel.x() + 10, panel.y() + 6);
        drawString(graphics, "HUD Editor", panel.x() + 27, panel.y() + 7, C_TEXT);
        boolean enabled = switch (selected) {
            case SCOREBOARD -> config.enabled && config.scoreboard.enabled;
            case ACTIONBAR -> HudCustomizer.isCustomHudEnabled();
            case PERFORMANCE -> config.enabled && config.visuals.enabled && config.visuals.performanceHud;
        };
        String status = !enabled ? "Disabled in settings" : dirty ? "Unsaved changes" : "Drag to arrange";
        drawString(graphics, status, panel.x() + panel.w() - getStringWidth(status) - 10,
                panel.y() + 7, dirty ? C_GOLD : C_TEXT_DIM);
        button(graphics, chip(0), "Scoreboard", selected == Target.SCOREBOARD, mx, my);
        button(graphics, chip(1), "Action bar", selected == Target.ACTIONBAR, mx, my);
        button(graphics, chip(2), "FPS / Ping / TPS", selected == Target.PERFORMANCE, mx, my);
        button(graphics, chip(3), grid ? "Grid: on" : "Grid: off", grid, mx, my);
        button(graphics, chip(4), "Reset", false, mx, my);
    }

    private void drawMoveIcon(Object graphics, int x, int y) {
        fillRect(graphics, x + 5, y, 1, 12, C_GOLD);
        fillRect(graphics, x, y + 5, 12, 1, C_GOLD);
        fillRect(graphics, x + 3, y + 1, 5, 1, C_GOLD);
        fillRect(graphics, x + 3, y + 10, 5, 1, C_GOLD);
        fillRect(graphics, x + 1, y + 3, 1, 5, C_GOLD);
        fillRect(graphics, x + 10, y + 3, 1, 5, C_GOLD);
    }

    private void drawFooter(Object graphics, int mx, int my) {
        Rect panel = footer();
        fillRoundRect(graphics, panel.x(), panel.y(), panel.w(), panel.h(), 6, 0xF018130F);
        drawRoundBorder(graphics, panel.x(), panel.y(), panel.w(), panel.h(), 6, C_BORDER);
        String position = String.format(Locale.ROOT, "%.2fx  X:%d  Y:%d", scale(), offsetX(), offsetY());
        drawString(graphics, position, panel.x() + 9, panel.y() + 9, C_TEXT);
        button(graphics, backButton(), "Back", false, mx, my);
        button(graphics, applyButton(), "Apply", true, mx, my);
        drawString(graphics, "Drag / wheel or corner: scale / arrows: move",
                panel.x() + 9, panel.y() + 28, C_TEXT_DIM);
        if (panel.w() >= 440) {
            String hint = "Shift: 8px   G: grid   Esc: discard";
            drawString(graphics, hint, panel.x() + panel.w() - getStringWidth(hint) - 9,
                    panel.y() + 28, C_TEXT_DIM);
        }
    }

    private void button(Object graphics, Rect r, String label, boolean active, int mx, int my) {
        boolean hovered = r.hit(mx, my);
        fillRoundRect(graphics, r.x(), r.y(), r.w(), r.h(), 4,
                hovered ? C_HOVER : active ? C_ACTIVE : C_SURFACE);
        drawRoundBorder(graphics, r.x(), r.y(), r.w(), r.h(), 4,
                active || hovered ? C_GOLD_DARK : C_BORDER);
        drawCenteredString(graphics, label, r.x() + r.w() / 2, r.y() + (r.h() - 8) / 2,
                active || hovered ? C_GOLD : C_TEXT_DIM);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event == null || event.button() != 0) return false;
        double mx = event.x();
        double my = event.y();
        if (applyButton().hit(mx, my)) { apply(); return true; }
        if (backButton().hit(mx, my)) { onClose(); return true; }
        if (chip(0).hit(mx, my)) { selected = Target.SCOREBOARD; return true; }
        if (chip(1).hit(mx, my)) { selected = Target.ACTIONBAR; return true; }
        if (chip(2).hit(mx, my)) { selected = Target.PERFORMANCE; return true; }
        if (chip(3).hit(mx, my)) { grid = !grid; return true; }
        if (chip(4).hit(mx, my)) { resetSelected(); return true; }
        if (toolbar().hit(mx, my) || footer().hit(mx, my)) return true;

        HudLayout.Bounds current = bounds(selected);
        if (handle(current).hit(mx, my)) {
            drag = Drag.RESIZE;
            resizeLeft = current.x();
            resizeTop = current.y();
            return true;
        }

        if (!hit(current, mx, my)) {
            for (Target target : Target.values()) if (hit(bounds(target), mx, my)) {
                selected = target;
                current = bounds(selected);
                break;
            }
        }
        if (hit(current, mx, my)) {
            drag = Drag.MOVE;
            grabX = (float) mx - current.x();
            grabY = (float) my - current.y();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (event == null || event.button() != 0 || drag == Drag.NONE) return false;
        if (drag == Drag.MOVE) {
            place((float) event.x() - grabX, (float) event.y() - grabY, scale(), grid);
        } else {
            float nextScale = HudLayout.scaleForHandle((float) event.x() - resizeLeft,
                    (float) event.y() - resizeTop, baseWidth(), baseHeight());
            place(resizeLeft, resizeTop, nextScale, false);
        }
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event == null || event.button() != 0) return false;
        boolean handled = drag != Drag.NONE;
        drag = Drag.NONE;
        return handled;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (toolbar().hit(mx, my) || footer().hit(mx, my)) return true;
        HudLayout.Bounds current = bounds(selected);
        if (!hit(current, mx, my)) {
            for (Target target : Target.values()) if (hit(bounds(target), mx, my)) {
                selected = target;
                current = bounds(selected);
                break;
            }
        }
        if (!hit(current, mx, my)) return false;
        place(current.x(), current.y(), scale() + (float) vertical * 0.05f, false);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event == null) return false;
        int key = event.key();
        if (key == 256) { onClose(); return true; }
        if (key == 71) { grid = !grid; return true; }
        if (key == 258) {
            selected = Target.values()[(selected.ordinal() + 1) % Target.values().length];
            return true;
        }
        if (key == 82) { resetSelected(); return true; }
        int step = (event.modifiers() & 1) != 0 ? GRID_SIZE : 1;
        int dx = key == 263 ? -step : key == 262 ? step : 0;
        int dy = key == 265 ? -step : key == 264 ? step : 0;
        if (dx != 0 || dy != 0) {
            HudLayout.Bounds current = bounds(selected);
            place(current.x() + dx, current.y() + dy, scale(), false);
            return true;
        }
        return super.keyPressed(event);
    }

    private void place(float left, float top, float scale, boolean snap) {
        if (selected == Target.PERFORMANCE) {
            float fitted = Math.clamp(scale, HudLayout.MIN_SCALE, HudLayout.MAX_SCALE);
            float widgetWidth = baseWidth() * fitted;
            float widgetHeight = baseHeight() * fitted;
            float nextLeft = snap ? Math.round(left / GRID_SIZE) * GRID_SIZE : left;
            float nextTop = snap ? Math.round(top / GRID_SIZE) * GRID_SIZE : top;
            nextLeft = Math.clamp(nextLeft, 0, Math.max(0, width - widgetWidth));
            nextTop = Math.clamp(nextTop, 0, Math.max(0, height - widgetHeight));
            performanceOffsetX = Math.round(nextLeft - 4);
            performanceOffsetY = Math.round(nextTop - 4);
            performanceScale = fitted;
            dirty = true;
            return;
        }
        HudLayout.Placement placement = HudLayout.fromTopLeft(selected == Target.SCOREBOARD,
                width, height, baseWidth(), baseHeight(), scale, left, top, snap);
        if (selected == Target.SCOREBOARD) {
            scoreboard.offsetX = placement.offsetX();
            scoreboard.offsetY = placement.offsetY();
            scoreboard.scale = placement.scale();
        } else if (selected == Target.ACTIONBAR) {
            actionbarOffsetX = placement.offsetX();
            actionbarOffsetY = placement.offsetY();
            actionbarScale = placement.scale();
        }
        dirty = true;
    }

    private void resetSelected() {
        if (selected == Target.SCOREBOARD) {
            scoreboard.offsetX = scoreboard.offsetY = 0;
            scoreboard.scale = 1;
        } else if (selected == Target.ACTIONBAR) {
            actionbarOffsetX = actionbarOffsetY = 0;
            actionbarScale = 1;
        } else {
            performanceOffsetX = performanceOffsetY = 0;
            performanceScale = 1;
        }
        dirty = true;
    }

    private void apply() {
        config.scoreboard.scale = scoreboard.scale;
        config.scoreboard.offsetX = scoreboard.offsetX;
        config.scoreboard.offsetY = scoreboard.offsetY;
        config.visuals.hudScale = actionbarScale;
        config.visuals.hudOffsetX = actionbarOffsetX;
        config.visuals.hudOffsetY = actionbarOffsetY;
        config.visuals.performanceHudScale = performanceScale;
        config.visuals.performanceHudOffsetX = performanceOffsetX;
        config.visuals.performanceHudOffsetY = performanceOffsetY;
        config.save();
        onClose();
    }

    @Override
    public void onClose() {
        drag = Drag.NONE;
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private HudLayout.Bounds bounds(Target target) {
        if (target == Target.SCOREBOARD) {
            return HudLayout.scoreboard(width, height, sidebarSize.width(), sidebarSize.height(),
                    scoreboard.scale, scoreboard.offsetX, scoreboard.offsetY);
        }
        if (target == Target.ACTIONBAR) return HudLayout.actionbar(width, height, actionbarWidth, actionbarHeight,
                actionbarScale, actionbarOffsetX, actionbarOffsetY);
        float scale = Math.clamp(performanceScale, HudLayout.MIN_SCALE, HudLayout.MAX_SCALE);
        float w = performanceWidth() * scale;
        float h = performanceHeight() * scale;
        return new HudLayout.Bounds(Math.clamp(4 + performanceOffsetX, 0, Math.max(0, width - w)),
                Math.clamp(4 + performanceOffsetY, 0, Math.max(0, height - h)), w, h, scale);
    }

    private int baseWidth() { return switch (selected) { case SCOREBOARD -> sidebarSize.width(); case ACTIONBAR -> actionbarWidth; case PERFORMANCE -> performanceWidth(); }; }
    private int baseHeight() { return switch (selected) { case SCOREBOARD -> sidebarSize.height(); case ACTIONBAR -> actionbarHeight; case PERFORMANCE -> performanceHeight(); }; }
    private float scale() { return switch (selected) { case SCOREBOARD -> scoreboard.scale; case ACTIONBAR -> actionbarScale; case PERFORMANCE -> performanceScale; }; }
    private int offsetX() { return switch (selected) { case SCOREBOARD -> scoreboard.offsetX; case ACTIONBAR -> actionbarOffsetX; case PERFORMANCE -> performanceOffsetX; }; }
    private int offsetY() { return switch (selected) { case SCOREBOARD -> scoreboard.offsetY; case ACTIONBAR -> actionbarOffsetY; case PERFORMANCE -> performanceOffsetY; }; }

    private String performanceText() {
        var visuals = config.visuals;
        java.util.List<String> values = new java.util.ArrayList<>();
        if (visuals.performanceShowFps) values.add("FPS 144");
        if (visuals.performanceShowPing) values.add("PING 42 ms");
        if (visuals.performanceShowTps) values.add("TPS 20.0");
        return values.isEmpty() ? "PERFORMANCE" : String.join("  |  ", values);
    }

    private int performanceWidth() { return Minecraft.getInstance().font.width(performanceText()) + 12; }
    private int performanceHeight() { return 19; }

    private Rect toolbar() {
        if (selected != Target.PERFORMANCE) return new Rect(6, 6, Math.min(540, width - 12), 44);
        HudLayout.Bounds performance = bounds(Target.PERFORMANCE);
        int rightX = Math.round(performance.x() + performance.width()) + 8;
        int rightWidth = width - rightX - 6;
        if (rightWidth >= 360) return new Rect(rightX, 6, Math.min(540, rightWidth), 44);
        int belowY = Math.round(performance.y() + performance.height()) + 8;
        if (belowY + 44 < height - 55) return new Rect(6, belowY, Math.min(540, width - 12), 44);
        return new Rect(6, 6, Math.min(540, width - 12), 44);
    }
    private Rect footer() { return new Rect(6, height - 49, width - 12, 43); }
    private Rect chip(int index) {
        Rect panel = toolbar();
        int available = panel.w() - 16;
        int w = available / 5;
        int x = panel.x() + 8 + index * w;
        return new Rect(x, panel.y() + 22, index == 4 ? available - w * 4 : w - 3, 17);
    }
    private Rect applyButton() { return new Rect(width - 70, height - 44, 55, 19); }
    private Rect backButton() { return new Rect(width - 123, height - 44, 48, 19); }
    private Rect handle(HudLayout.Bounds bounds) {
        return new Rect(Math.max(0, Math.min(width - HANDLE_SIZE,
                Math.round(bounds.x() + bounds.width()) - HANDLE_SIZE + 3)),
                Math.max(0, Math.min(height - HANDLE_SIZE,
                        Math.round(bounds.y() + bounds.height()) - HANDLE_SIZE + 3)), HANDLE_SIZE, HANDLE_SIZE);
    }
    private static boolean hit(HudLayout.Bounds b, double x, double y) {
        return x >= b.x() - 3 && x <= b.x() + b.width() + 3
                && y >= b.y() - 3 && y <= b.y() + b.height() + 3;
    }
    private record Rect(int x, int y, int w, int h) {
        boolean hit(double px, double py) { return px >= x && px < x + w && py >= y && py < y + h; }
    }
}
