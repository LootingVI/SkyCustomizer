package de.flori.skycustomizer.config;

import de.flori.skycustomizer.features.ScoreboardCustomizer;
import de.flori.skycustomizer.features.ScoreboardCustomizer.SidebarLine;
import de.flori.skycustomizer.features.ScoreboardCustomizer.SidebarSnapshot;
import de.flori.skycustomizer.render.ScoreboardBackgrounds;
import de.flori.skycustomizer.util.ScoreboardLines;
import de.flori.skycustomizer.util.TextUtil;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.*;
import static de.flori.skycustomizer.render.ModernRenderHelper.*;
import static de.flori.skycustomizer.config.StudioWidgets.*;

public final class ScoreboardEditorScreen extends StudioScreen {
    private static final String TITLE = "@title";
    private final Screen parent;
    private final Consumer<SkyCustomizerConfig.ScoreboardConfig> apply;
    private SkyCustomizerConfig.ScoreboardConfig staged;
    private final List<Action> actions = new ArrayList<>();
    private final Map<String, NumberField> numbers = new LinkedHashMap<>();
    private EditBox inline, imagePath;
    private String selected = "", status = "Click a title or line to edit it directly.";
    private boolean loading, editing, showHidden, importing, compactOptions;
    private boolean standalone, reloadPlacement;
    private int page, scroll, contentHeight, x, y, w, h, top, bottom, canvasX, canvasW, optionsX, optionsW;
    private float boardX, boardY, boardScale = 1;
    private int boardWidth;
    private SidebarSnapshot preview, source;
    private long refreshAt;
    private record Action(int x, int y, int w, int h, Runnable run) {}
    private record NumberField(String label, EditBox box, DoubleSupplier get, DoubleConsumer set, double min, double max, double step) {}

    public ScoreboardEditorScreen(Screen parent) {
        this(parent, SkyCustomizerConfig.get().scoreboard, value -> {
            var cfg = SkyCustomizerConfig.get(); cfg.scoreboard = value; cfg.save();
        });
        standalone = true;
    }
    public ScoreboardEditorScreen(Screen parent, SkyCustomizerConfig.ScoreboardConfig style,
                                  Consumer<SkyCustomizerConfig.ScoreboardConfig> apply) {
        super("Scoreboard Studio");
        this.parent = parent; this.apply = apply; this.staged = ScoreboardStyles.copy(style);
        ScoreboardLines.ensureKeys(staged);
    }
    @Override protected String[] historyFields() { return new String[]{"staged"}; }
    @Override protected void afterHistory() { invalidate(); refreshNumbers(); loadSelection(); }

    @Override protected void init() {
        if (reloadPlacement) {
            var saved = SkyCustomizerConfig.get().scoreboard;
            staged.scale = saved.scale; staged.offsetX = saved.offsetX; staged.offsetY = saved.offsetY;
            reloadPlacement = false;
        }
        clearWidgets(); numbers.clear(); actions.clear(); layout();
        inline = edit("Selected scoreboard text", "", 1024);
        inline.setResponder(value -> {
            if (loading || selected.isEmpty()) return;
            if (TITLE.equals(selected)) { staged.customTitle = value; staged.customTitleEnabled = true; staged.hideHypixelTitle = false; }
            else ScoreboardLines.edit(staged, selected, value);
            invalidate();
        });
        addWidget(inline);
        imagePath = edit("Image file path", "", 1024); addWidget(imagePath);
        number("opacity", "Card opacity", () -> staged.backgroundOpacity, v -> staged.backgroundOpacity = (int)v, 0, 255, 5);
        number("imageOpacity", "Image opacity", () -> staged.backgroundImageOpacity, v -> staged.backgroundImageOpacity = (int)v, 0, 255, 5);
        number("shade", "Darken image", () -> staged.backgroundImageShade, v -> staged.backgroundImageShade = (int)v, 0, 255, 5);
        number("scale", "HUD scale", () -> staged.scale, v -> staged.scale = (float)v, .45, 3, .05);
        number("x", "Horizontal offset", () -> staged.offsetX, v -> staged.offsetX = (int)v, -10000, 10000, 1);
        number("y", "Vertical offset", () -> staged.offsetY, v -> staged.offsetY = (int)v, -10000, 10000, 1);
        number("padding", "Padding", () -> staged.padding, v -> staged.padding = (int)v, 2, 14, 1);
        number("spacing", "Line spacing", () -> staged.lineSpacing, v -> staged.lineSpacing = (int)v, 0, 5, 1);
        number("lineScale", "Line font scale", () -> staged.lineFontScale, v -> staged.lineFontScale = (float)v, .6, 2, .05);
        number("lineAnimation", "Line animation speed", () -> staged.lineAnimationSpeed, v -> staged.lineAnimationSpeed = (float)v, .1, 4, .05);
        refreshNumbers(); loadSelection(); startHistory();
        if (editing) { setFocused(inline); inline.setFocused(true); }
    }
    private void number(String id, String label, DoubleSupplier get, DoubleConsumer set, double min, double max, double step) {
        EditBox box = edit(label, "", 12);
        var field = new NumberField(label, box, get, set, min, max, step);
        numbers.put(id, field); addWidget(box);
        box.setResponder(text -> {
            if (loading) return;
            Double value = valid(field);
            box.setTextColor(value == null ? C_RED : C_TEXT);
            if (value != null) { set.accept(value); invalidate(); }
        });
    }
    private Double valid(NumberField field) {
        try {
            double value = Double.parseDouble(field.box.getValue());
            return Double.isFinite(value) && value >= field.min && value <= field.max
                    && (field.step < 1 || value == Math.rint(value)) ? value : null;
        } catch (NumberFormatException e) { return null; }
    }
    private void refreshNumbers() {
        loading = true;
        for (var field : numbers.values()) {
            field.box.setValue(field.step < 1 ? String.format(Locale.ROOT, "%.2f", field.get.getAsDouble())
                    : Integer.toString((int)field.get.getAsDouble()));
            field.box.setTextColor(C_TEXT);
        }
        loading = false;
    }
    private void layout() {
        w = Math.min(880, width - 16); h = Math.min(530, height - 16); x = (width - w) / 2; y = (height - h) / 2;
        top = y + 76; bottom = y + h - 45;
        boolean compact = w < 580;
        canvasX = x + 12;
        canvasW = compact ? w - 24 : Math.max(220, (w - 36) * 55 / 100);
        optionsX = compact ? x + 12 : canvasX + canvasW + 12;
        optionsW = compact ? w - 24 : w - canvasW - 36;
    }
    private boolean canvasVisible() { return w >= 580 || !compactOptions; }
    private boolean optionsVisible() { return w >= 580 || compactOptions; }
    private void invalidate() { preview = null; source = null; }
    private void refresh() {
        if (source == null || System.nanoTime() >= refreshAt) {
            source = ScoreboardCustomizer.snapshot(staged, true);
            preview = showHidden ? source : ScoreboardCustomizer.snapshot(staged);
            refreshAt = System.nanoTime() + (staged.chromaTitle ? 33_000_000L : 100_000_000L);
        }
    }
    private SidebarLine selectedLine() {
        refresh();
        return source.lines().stream().filter(line -> line.id().equals(selected)).findFirst().orElse(null);
    }
    private void loadSelection() {
        if (inline == null) return;
        String value = "";
        if (TITLE.equals(selected)) value = staged.customTitleEnabled ? staged.customTitle : TextUtil.editableFormatting(source == null ? ScoreboardCustomizer.snapshot(staged).title() : source.title());
        else {
            SidebarLine line = selectedLine();
            if (line != null) {
                int custom = staged.customLineKeys.indexOf(selected);
                value = custom >= 0 ? staged.customLines.get(custom) : staged.lineOverrides.getOrDefault(selected, TextUtil.editableFormatting(line.name()));
            } else { selected = ""; editing = false; }
        }
        loading = true; inline.setValue(value); inline.moveCursorToStart(false); loading = false;
    }
    private void select(String id, boolean edit) {
        checkpoint(); selected = id; editing = edit; loadSelection();
        if (id.startsWith("server:")) status = "Edited rows use fixed text. Reset restores live server text.";
        if (edit) { compactOptions = false; setFocused(inline); inline.setFocused(true); }
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float dt) {
        layout(); refresh(); actions.clear();
        inline.visible = false; imagePath.visible = false;
        for (var field : numbers.values()) field.box.visible = false;
        backdrop(g, dt); fillRect(g, 0, 0, width, height, 0xAD080604);
        drawShadow(g, x, y, w, h, 10); fillRoundRect(g, x, y, w, h, 10, C_PANEL);
        icon(g, 3, x + 14, y + 12, 22, C_GOLD);
        drawString(g, "Scoreboard Studio", x + 44, y + 12, C_TEXT);
        drawString(g, fit("Images, live content and direct editing", w - 64), x + 44, y + 28, C_TEXT_DIM);
        String[] tabs = {"Lines", "Style", "Image"};
        for (int i = 0; i < tabs.length; i++) {
            int tab = i;
            buttonAt(g, tabs[i], x + 12 + i * 73, y + 46, 67, mx, my, page == i, false,
                    () -> { page = tab; scroll = 0; editing = false; setFocused(null); if (w < 580) compactOptions = true; });
        }
        if (w < 580) buttonAt(g, compactOptions ? "Board" : "Options", x + w - 83, y + 46, 71, mx, my, !compactOptions, false,
                () -> { compactOptions = !compactOptions; editing = false; });
        if (canvasVisible()) drawCanvas(g, mx, my, dt);
        if (optionsVisible()) drawOptions(g, mx, my, dt);
        int fy = y + h - 31;
        buttonAt(g, "Undo", x + 12, fy, 48, mx, my, false, false, () -> undo(false));
        buttonAt(g, "Redo", x + 66, fy, 48, mx, my, false, false, () -> undo(true));
        buttonAt(g, "Discard", x + w - 154, fy, 70, mx, my, false, false, this::onClose);
        buttonAt(g, "Save", x + w - 78, fy, 66, mx, my, true, false, this::save);
        if (w >= 580) drawString(g, fit(status, w - 300), x + 126, fy + 8, C_TEXT_DIM);
    }
    private void drawCanvas(GuiGraphicsExtractor g, int mx, int my, float dt) {
        fillRoundRect(g, canvasX, top, canvasW, bottom - top, 8, 0xFF100D0A);
        drawRoundBorder(g, canvasX, top, canvasW, bottom - top, 8, C_BORDER);
        drawString(g, showHidden ? "EDIT VIEW · ALL ROWS" : "LIVE PREVIEW", canvasX + 12, top + 10, C_GOLD);
        drawString(g, "Click text to edit. Enter to finish.", canvasX + 12, top + 24, C_TEXT_DIM);
        var size = ScoreboardCustomizer.measure(preview, staged);
        boardWidth = size.width();
        boardScale = Math.max(.1f, Math.min(1.6f, Math.min((canvasW - 30f) / Math.max(1, size.width()),
                (bottom - top - 74f) / Math.max(1, size.height()))));
        boardX = canvasX + (canvasW - size.width() * boardScale) / 2;
        boardY = top + 44 + Math.max(0, (bottom - top - 74 - size.height() * boardScale) / 2);
        g.enableScissor(canvasX + 1, top + 39, canvasX + canvasW - 1, bottom - 22);
        ScoreboardCustomizer.draw(g, preview, staged, boardX, boardY, boardScale);
        int hover = ScoreboardLines.hit(staged, preview.showTitle(), preview.lines().size(), boardWidth,
                (mx - boardX) / boardScale, (my - boardY) / boardScale);
        for (int i = -2; i < preview.lines().size(); i++) {
            if (i == -1 || i == -2 && !preview.showTitle()) continue;
            String id = i == -2 ? TITLE : preview.lines().get(i).id();
            int rowY = i == -2 ? Math.clamp(staged.padding, 2, 14) : ScoreboardLines.rowY(staged, preview.showTitle(), i);
            int lx = Math.round(boardX), ly = Math.round(boardY + rowY * boardScale);
            int lw = Math.round(size.width() * boardScale), lh = Math.max(8, Math.round(10 * boardScale));
            if (id.equals(selected) || hover == i) drawRoundBorder(g, lx, ly - 1, lw, lh, 2, id.equals(selected) ? C_GOLD : 0xAAE2944E);
            if (i >= 0 && preview.lines().get(i).hidden()) fillRect(g, lx + 2, ly + lh / 2, lw - 4, 1, C_RED);
        }
        g.disableScissor();
        drawString(g, fit(status, canvasW - 24), canvasX + 12, bottom - 16, C_TEXT_DIM);
        if (editing && !selected.isEmpty()) {
            int index = -1;
            for (int i = 0; i < preview.lines().size(); i++) if (preview.lines().get(i).id().equals(selected)) { index = i; break; }
            int ry = TITLE.equals(selected) ? Math.clamp(staged.padding, 2, 14) : index >= 0 ? ScoreboardLines.rowY(staged, preview.showTitle(), index) : 0;
            int popupY = Math.clamp(Math.round(boardY + ry * boardScale) - 9, top + 39, Math.max(top + 39, bottom - 65));
            int popupW = canvasW - 16;
            fillRoundRect(g, canvasX + 8, popupY - 3, popupW, 58, 6, C_PANEL);
            drawRoundBorder(g, canvasX + 8, popupY - 3, popupW, 58, 6, C_GOLD);
            inline.visible = true; field(g, inline, canvasX + 11, popupY, popupW - 6, mx, my, dt);
            int bw = Math.max(40, (popupW - 16) / 3);
            buttonAt(g, "Color", canvasX + 12, popupY + 27, bw, mx, my, false, false, this::textColor);
            buttonAt(g, "Bold", canvasX + 16 + bw, popupY + 27, bw, mx, my, false, false, this::boldText);
            buttonAt(g, "Done", canvasX + 20 + bw * 2, popupY + 27, bw, mx, my, true, false, () -> { editing = false; setFocused(null); });
        }
    }
    private void drawOptions(GuiGraphicsExtractor g, int mx, int my, float dt) {
        fillRoundRect(g, optionsX, top, optionsW, bottom - top, 8, C_SURFACE);
        g.enableScissor(optionsX, top, optionsX + optionsW, bottom);
        int cy = top + 12 - scroll;
        if (page == 0) {
            cy = caption(g, "CONTENT", cy);
            cy = rowButtons(g, cy, mx, my, new String[]{"Edit title", showHidden ? "Hide extras" : "All rows"},
                    new Runnable[]{() -> select(TITLE, true), () -> { showHidden = !showHidden; invalidate(); }});
            cy = rowButtons(g, cy, mx, my, new String[]{"Add line", "Add spacer"}, new Runnable[]{() -> addLine("&fNew line"), () -> addLine("")});
            if (!selected.isEmpty()) {
                cy = caption(g, TITLE.equals(selected) ? "TITLE SELECTED" : "LINE SELECTED", cy + 4);
                cy = rowButtons(g, cy, mx, my, new String[]{"Edit text", "Color"}, new Runnable[]{() -> select(selected, true), this::textColor});
                if (!TITLE.equals(selected)) {
                    var line = selectedLine();
                    boolean hidden = staged.hiddenLineIds.contains(selected);
                    cy = rowButtons(g, cy, mx, my, new String[]{hidden ? "Show line" : "Hide line", selected.startsWith("custom:") ? "Delete" : "Reset text"},
                            new Runnable[]{() -> {
                                if (line != null && line.hidden() && !hidden) { status = "Hidden by an advanced text filter."; return; }
                                if (!staged.hiddenLineIds.remove(selected)) staged.hiddenLineIds.add(selected);
                                editing = false; invalidate();
                            }, () -> {
                                if (selected.startsWith("custom:")) { ScoreboardLines.remove(staged, selected); selected = ""; editing = false; }
                                else staged.lineOverrides.remove(selected);
                                invalidate(); loadSelection();
                            }});
                    cy = rowButtons(g, cy, mx, my, new String[]{"Move up", "Move down"}, new Runnable[]{() -> move(-1), () -> move(1)});
                }
            }
            cy = caption(g, "ROWS · FIRST 15 VISIBLE IN GAME", cy + 5);
            for (SidebarLine line : source.lines()) {
                String text = line.name().getString();
                if (text.isBlank()) text = "(spacer)";
                String label = (line.hidden() ? "Hidden · " : "") + text;
                buttonAt(g, fit(label, optionsW - 38), optionsX + 10, cy, optionsW - 20, mx, my, line.id().equals(selected), true, () -> select(line.id(), true));
                cy += 29;
            }
            cy = rowButtons(g, cy + 4, mx, my, new String[]{"Server order"}, new Runnable[]{() -> { staged.lineOrder.clear(); invalidate(); }});
        } else if (page == 1) {
            cy = caption(g, "STYLE PRESETS", cy);
            cy = rowButtons(g, cy, mx, my, new String[]{"Reference", "Minimal", "Dark"}, new Runnable[]{
                    () -> preset(0), () -> preset(1), () -> preset(2)});
            cy = toggle(g, cy, mx, my, "Custom scoreboard", staged.enabled, () -> staged.enabled = !staged.enabled);
            cy = toggle(g, cy, mx, my, "Custom title", staged.customTitleEnabled, () -> staged.customTitleEnabled = !staged.customTitleEnabled);
            cy = toggle(g, cy, mx, my, "Hide vanilla title", staged.hideHypixelTitle, () -> staged.hideHypixelTitle = !staged.hideHypixelTitle);
            cy = toggle(g, cy, mx, my, "Hide score numbers", staged.hideScoreNumbers, () -> staged.hideScoreNumbers = !staged.hideScoreNumbers);
            cy = toggle(g, cy, mx, my, "Chroma title", staged.chromaTitle, () -> staged.chromaTitle = !staged.chromaTitle);
            cy = toggle(g, cy, mx, my, "Title shadow", staged.titleShadow, () -> staged.titleShadow = !staged.titleShadow);
            cy = toggle(g, cy, mx, my, "Compact spacing", staged.compactStyle, () -> staged.compactStyle = !staged.compactStyle);
            cy = toggle(g, cy, mx, my, "Rounded background", staged.roundedBackground, () -> staged.roundedBackground = !staged.roundedBackground);
            cy = toggle(g, cy, mx, my, "Animate line color", staged.lineAnimation, () -> staged.lineAnimation = !staged.lineAnimation);
            cy = rowButtons(g, cy, mx, my, new String[]{"Title color", "Card color", "Line color"}, new Runnable[]{
                    () -> minecraft.setScreen(new ColorPickerScreen(this, staged.titleColorHex, color -> {
                        staged.titleColorHex = color; staged.customTitle = TextUtil.stripLegacyFormatting(staged.customTitle); invalidate(); loadSelection();
                    })), this::cardColor, () -> minecraft.setScreen(new ColorPickerScreen(this, staged.lineColorHex, color -> {
                        staged.lineColorHex = color; invalidate();
                    }))});
            cy = rowButtons(g, cy, mx, my, new String[]{"Score color", "Reset line colors"}, new Runnable[]{
                    () -> minecraft.setScreen(new ColorPickerScreen(this, staged.scoreColorHex, color -> {
                        staged.scoreColorHex = color; invalidate();
                    })), () -> { staged.lineColorHex = ""; staged.scoreColorHex = ""; invalidate(); }});
            for (String id : List.of("scale", "x", "y", "padding", "spacing", "lineScale", "lineAnimation")) cy = drawNumber(g, id, cy, mx, my, dt);
            if (standalone) cy = rowButtons(g, cy, mx, my, new String[]{"Save & position HUD"}, new Runnable[]{() -> {
                if (save()) { reloadPlacement = true; minecraft.setScreen(new HudEditorScreen(this)); }
            }});
        } else {
            cy = caption(g, "IMAGE GALLERY", cy);
            cy = drawGallery(g, cy, mx, my);
            cy = caption(g, "IMPORT A NEW IMAGE", cy + 3);
            drawString(g, fit("PNG / JPEG · up to 12 MB", optionsW - 20), optionsX + 10, cy, C_TEXT_DIM); cy += 18;
            drawString(g, fit("Drop a file anywhere on this editor.", optionsW - 20), optionsX + 10, cy, C_TEXT_DIM); cy += 22;
            cy = rowButtons(g, cy, mx, my, new String[]{importing ? "Importing..." : "Choose image", "Remove"},
                    new Runnable[]{() -> { if (!importing) importImage(ScoreboardBackgrounds.choose()); },
                            () -> { staged.backgroundImage = ""; staged.backgroundImageEnabled = false; invalidate(); }});
            drawString(g, fit(ScoreboardBackgrounds.status(staged.backgroundImage), optionsW - 20), optionsX + 10, cy, C_GOLD); cy += 23;
            drawString(g, "Or paste a local file path:", optionsX + 10, cy, C_TEXT_DIM); cy += 16;
            if (cy >= top && cy + 26 <= bottom) { imagePath.visible = true; field(g, imagePath, optionsX + 10, cy, optionsW - 20, mx, my, dt); }
            cy += 31;
            cy = rowButtons(g, cy, mx, my, new String[]{"Import path"}, new Runnable[]{() -> {
                if (importing) return;
                try { String path = imagePath.getValue().strip(); if (path.startsWith("\"") && path.endsWith("\"")) path = path.substring(1, path.length()-1);
                    importImage(ScoreboardBackgrounds.importFile(Path.of(path)));
                } catch (RuntimeException e) { status = "Enter a valid local image path."; }
            }});
            cy = toggle(g, cy, mx, my, "Background card", staged.backgroundEnabled, () -> staged.backgroundEnabled = !staged.backgroundEnabled);
            cy = toggle(g, cy, mx, my, "Image enabled", staged.backgroundImageEnabled, () -> staged.backgroundImageEnabled = !staged.backgroundImageEnabled);
            cy = rowButtons(g, cy, mx, my, new String[]{"Fit: " + staged.backgroundImageFit}, new Runnable[]{() -> {
                var modes = List.of("COVER", "CONTAIN", "STRETCH");
                staged.backgroundImageFit = modes.get((modes.indexOf(staged.backgroundImageFit) + 1) % modes.size());
            }});
            for (String id : List.of("imageOpacity", "shade", "opacity")) cy = drawNumber(g, id, cy, mx, my, dt);
            cy = rowButtons(g, cy, mx, my, new String[]{"Card color"}, new Runnable[]{this::cardColor});
            drawString(g, fit(status, optionsW - 20), optionsX + 10, cy + 5, C_TEXT_DIM); cy += 28;
        }
        contentHeight = cy - top + scroll + 8;
        scroll = Math.clamp(scroll, 0, Math.max(0, contentHeight - (bottom - top)));
        g.disableScissor();
        if (contentHeight > bottom - top) {
            int track = bottom - top - 8, thumb = Math.max(18, track * (bottom - top) / contentHeight);
            int ty = top + 4 + Math.round((track - thumb) * scroll / (float)Math.max(1, contentHeight - (bottom - top)));
            fillRoundRect(g, optionsX + optionsW - 4, ty, 3, thumb, 2, C_GOLD_DARK);
        }
    }
    private int caption(GuiGraphicsExtractor g, String text, int cy) { drawString(g, fit(text, optionsW - 20), optionsX + 10, cy, C_GOLD); return cy + 20; }
    private int drawGallery(GuiGraphicsExtractor g, int cy, int mx, int my) {
        List<String> images = ScoreboardBackgrounds.gallery();
        if (images.isEmpty()) {
            drawString(g, "Import an image to start your gallery.", optionsX + 10, cy, C_TEXT_DIM);
            return cy + 23;
        }
        int gap = 5;
        int cellWidth = Math.max(56, (optionsW - 20 - gap) / 2);
        int cellHeight = 58;
        for (int index = 0; index < images.size(); index++) {
            String image = images.get(index);
            int column = index % 2;
            int row = index / 2;
            int bx = optionsX + 10 + column * (cellWidth + gap);
            int by = cy + row * (cellHeight + gap);
            boolean selectedImage = image.equals(staged.backgroundImage);
            if (by + cellHeight >= top && by <= bottom) {
                fillRoundRect(g, bx, by, cellWidth, cellHeight, 5, selectedImage ? C_ACTIVE : C_INPUT);
                if (!ScoreboardBackgrounds.drawThumbnail(g, image, bx + 3, by + 3, cellWidth - 6, 35))
                    drawCenteredString(g, "Loading...", bx + cellWidth / 2, by + 16, C_TEXT_DIM);
                drawRoundBorder(g, bx, by, cellWidth, cellHeight, 5, selectedImage ? C_GOLD : C_BORDER);
                String label = selectedImage ? "ACTIVE" : "IMAGE " + (index + 1);
                drawCenteredString(g, label, bx + cellWidth / 2, by + 43, selectedImage ? C_GOLD : C_TEXT_DIM);
                actions.add(new Action(bx, by, cellWidth, cellHeight, () -> {
                    staged.backgroundImage = image;
                    staged.backgroundImageEnabled = true;
                    staged.backgroundEnabled = true;
                    ScoreboardBackgrounds.retry(image);
                    status = "Gallery image selected. Save to apply it.";
                    invalidate();
                }));
            }
        }
        return cy + ((images.size() + 1) / 2) * (cellHeight + gap);
    }
    private int rowButtons(GuiGraphicsExtractor g, int cy, int mx, int my, String[] labels, Runnable[] runs) {
        int bw = (optionsW - 20 - (labels.length - 1) * 5) / labels.length;
        for (int i = 0; i < labels.length; i++) buttonAt(g, fit(labels[i], bw - 10), optionsX + 10 + i * (bw + 5), cy, bw, mx, my, false, true, runs[i]);
        return cy + 31;
    }
    private int toggle(GuiGraphicsExtractor g, int cy, int mx, int my, String label, boolean value, Runnable run) {
        buttonAt(g, fit(label, optionsW - 74) + (value ? " · ON" : " · OFF"), optionsX + 10, cy, optionsW - 20, mx, my, value, true, run);
        return cy + 31;
    }
    private int drawNumber(GuiGraphicsExtractor g, String id, int cy, int mx, int my, float dt) {
        var field = numbers.get(id);
        drawString(g, field.label, optionsX + 10, cy + 3, C_TEXT); cy += 18;
        int iw = optionsW - 82;
        if (cy >= top && cy + 26 <= bottom) { field.box.visible = true; field(g, field.box, optionsX + 39, cy, iw, mx, my, dt); }
        buttonAt(g, "-", optionsX + 10, cy, 24, mx, my, false, true, () -> adjust(field, -1));
        buttonAt(g, "+", optionsX + optionsW - 34, cy, 24, mx, my, false, true, () -> adjust(field, 1));
        return cy + 33;
    }
    private void adjust(NumberField field, int direction) {
        field.set.accept(Math.clamp(field.get.getAsDouble() + direction * field.step, field.min, field.max));
        refreshNumbers(); invalidate();
    }
    private void buttonAt(GuiGraphicsExtractor g, String text, int bx, int by, int bw, int mx, int my, boolean active, boolean clipped, Runnable run) {
        button(g, text, bx, by, bw, mx, my, active);
        if (!clipped || by >= top && by + 24 <= bottom) actions.add(new Action(bx, by, bw, 24, run));
    }
    private void cardColor() { minecraft.setScreen(new ColorPickerScreen(this, staged.backgroundColorHex, color -> { staged.backgroundColorHex = color; invalidate(); })); }
    private void textColor() {
        if (selected.isEmpty()) { status = "Select a title or line first."; return; }
        String before = inline.getValue();
        minecraft.setScreen(new ColorPickerScreen(this, "#FFFF55", color -> {
            var formatted = TextUtil.parseLegacyFormatting(before, net.minecraft.network.chat.Style.EMPTY);
            inline.setValue(TextUtil.editableFormatting(TextUtil.recolorRange(formatted, 0, formatted.getString().length(),
                    de.flori.skycustomizer.util.ColorUtil.fromHex(color) & 0xFFFFFF)));
            editing = true; invalidate();
        }));
    }
    private void boldText() {
        var formatted = TextUtil.parseLegacyFormatting(inline.getValue(), net.minecraft.network.chat.Style.EMPTY);
        if (formatted.getString().isEmpty()) { inline.insertText("&l"); return; }
        boolean alreadyBold = formatted.visit((style, text) -> !text.isEmpty() && !style.isBold()
                ? Optional.of(false) : Optional.<Boolean>empty(), net.minecraft.network.chat.Style.EMPTY).orElse(true);
        var result = net.minecraft.network.chat.Component.empty();
        formatted.visit((style, text) -> {
            result.append(net.minecraft.network.chat.Component.literal(text).withStyle(style.withBold(!alreadyBold)));
            return Optional.empty();
        }, net.minecraft.network.chat.Style.EMPTY);
        inline.setValue(TextUtil.editableFormatting(result));
    }
    private void preset(int id) {
        switch (id) { case 0 -> ScoreboardStyles.applyScreenshot(staged); case 1 -> ScoreboardStyles.applyMinimal(staged); default -> ScoreboardStyles.applyDark(staged); }
        refreshNumbers(); invalidate(); loadSelection();
    }
    private void addLine(String text) {
        if (staged.customLines.size() >= 64) { status = "At most 64 saved custom lines."; return; }
        refresh();
        var order = new ArrayList<>(source.lines().stream().map(SidebarLine::id).toList());
        int after = order.indexOf(selected);
        String id = ScoreboardLines.add(staged, text);
        order.add(after < 0 ? 0 : after + 1, id);
        for (String saved : staged.lineOrder) if (!order.contains(saved)) order.add(saved);
        staged.lineOrder = order; invalidate(); select(id, true);
        status = "Custom line added. Only the first 15 visible rows render.";
    }
    private void move(int direction) { refresh(); ScoreboardLines.move(staged, source.lines().stream().map(SidebarLine::id).toList(), selected, direction); invalidate(); }
    private void importImage(CompletableFuture<String> task) {
        importing = true; status = "Importing image...";
        task.whenComplete((file, error) -> minecraft.execute(() -> {
            importing = false;
            if (error != null) { Throwable cause = error; while (cause.getCause() != null) cause = cause.getCause(); status = Objects.toString(cause.getMessage(), "Image import failed."); return; }
            if (file == null) { status = "Image selection cancelled."; return; }
            ScoreboardBackgrounds.retry(file);
            checkpoint(); staged.backgroundImage = file; staged.backgroundImageEnabled = true; staged.backgroundEnabled = true;
            invalidate(); checkpoint(); status = "Image imported. Save to keep this background.";
        }));
    }
    @Override public void onFilesDrop(List<Path> paths) {
        if (paths.size() != 1) { status = "Drop one PNG or JPEG image at a time."; return; }
        if (!importing) { page = 2; scroll = 0; editing = false; importImage(ScoreboardBackgrounds.importFile(paths.getFirst())); }
    }
    private boolean save() {
        if (importing) { status = "Wait for the image import before saving."; return false; }
        for (var field : numbers.values()) if (valid(field) == null) { status = "Check " + field.label + " before saving."; return false; }
        apply.accept(ScoreboardStyles.copy(staged)); status = "Scoreboard saved."; editing = false; checkpoint();
        return true;
    }
    @Override public boolean mouseClicked(MouseButtonEvent e, boolean twice) {
        if (e.button() != 0) return super.mouseClicked(e, twice);
        refresh();
        for (int i = actions.size() - 1; i >= 0; i--) {
            var action = actions.get(i);
            if (hit(e.x(), e.y(), action.x, action.y, action.w, action.h)) {
                checkpoint(); action.run.run(); invalidate(); checkpoint(); return true;
            }
        }
        if (inline.visible && inline.isMouseOver(e.x(), e.y())) return super.mouseClicked(e, twice);
        if (canvasVisible() && hit(e.x(), e.y(), canvasX, top + 39, canvasW, bottom - top - 61)) {
            int index = ScoreboardLines.hit(staged, preview.showTitle(), preview.lines().size(), boardWidth,
                    (e.x() - boardX) / boardScale, (e.y() - boardY) / boardScale);
            if (index == -2) select(TITLE, true);
            else if (index >= 0) select(preview.lines().get(index).id(), true);
            else { editing = false; setFocused(null); }
            return true;
        }
        return super.mouseClicked(e, twice);
    }
    @Override public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (optionsVisible() && hit(mx, my, optionsX, top, optionsW, bottom - top)) {
            scroll = Math.clamp(scroll - (int)(vertical * 26), 0, Math.max(0, contentHeight - (bottom - top))); return true;
        }
        return super.mouseScrolled(mx, my, horizontal, vertical);
    }
    @Override public boolean keyPressed(KeyEvent e) {
        if ((e.modifiers() & 2) != 0 && e.key() == 83) { save(); return true; }
        if (editing && (e.key() == 256 || e.key() == 257 || e.key() == 335)) { editing = false; setFocused(null); checkpoint(); return true; }
        return super.keyPressed(e);
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
    private String fit(String text, int width) {
        if (text == null) return "";
        if (getStringWidth(text) <= width) return text;
        while (!text.isEmpty() && getStringWidth(text + "...") > width) text = text.substring(0, text.length() - 1);
        return text + "...";
    }
}
