package de.flori.skycustomizer.config;

import de.flori.skycustomizer.util.LevelColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.Map;
import java.util.TreeMap;

import static de.flori.skycustomizer.render.ModernRenderHelper.*;

public final class LevelColorEditorScreen extends StudioScreen {

    private static final int MAX_PANEL_W = 820;
    private static final int MAX_PANEL_H = 540;
    private static final int HEADER_H = 50;
    private static final int FOOTER_H = 42;
    private static final int LIST_ROW_H = 28;
    private static final int HUE_H = 14;

    private final Screen parent;
    private final SkyCustomizerConfig cfg;
    private final TreeMap<Integer, String> workingColors = new TreeMap<>();

    private boolean loaded;
    private boolean dirty;
    private boolean levelEnabled;
    private boolean overrideHypixelColors;
    private boolean smoothGradient;
    private boolean chromaAtSixHundred;
    private float chromaSpeed;

    private int selectedThreshold;
    private float hue;
    private float saturation;
    private float value;
    private String hexInput = "#FFFFFF";
    private String redInput = "255";
    private String greenInput = "255";
    private String blueInput = "255";
    private String thresholdInput = "0";
    private String bracketInput = "#555555";
    private Field activeField = Field.NONE;
    private DragTarget dragTarget = DragTarget.NONE;

    private int panelW;
    private int panelH;
    private int px;
    private int py;
    private int listScroll;
    private String toast = "";
    private int toastTicks;

    private enum Field { NONE, THRESHOLD, HEX, RED, GREEN, BLUE, BRACKET }
    private enum DragTarget { NONE, SATURATION_VALUE, HUE }

    public LevelColorEditorScreen(Screen parent) {
        super("Level Color Editor");
        this.parent = parent;
        this.cfg = SkyCustomizerConfig.get();
    }

    @Override protected String[] historyFields() { return new String[]{"workingColors","levelEnabled","overrideHypixelColors","smoothGradient","chromaAtSixHundred","chromaSpeed","selectedThreshold","hue","saturation","value","hexInput","redInput","greenInput","blueInput","thresholdInput","bracketInput"}; }
    @Override protected void afterHistory() { dirty=true; }

    @Override
    protected void init() {
        clearWidgets();
        updateLayout();
        if (!loaded) {
            loadWorkingCopy();
            loaded = true;
        } else {
            normalizeSelection();
        }
    }

    private void loadWorkingCopy() {
        workingColors.clear();
        if (cfg.level.customColors != null) {
            for (Map.Entry<Integer, String> entry : cfg.level.customColors.entrySet()) {
                if (entry.getKey() != null) {
                    workingColors.put(clamp(entry.getKey(), 0, 9999), normalHex(entry.getValue()));
                }
            }
        }
        if (workingColors.isEmpty()) {
            workingColors.putAll(LevelColorUtil.getDefaultExtendedHexMap());
        }

        levelEnabled = cfg.level.enabled;
        overrideHypixelColors = cfg.level.overrideHypixelColors;
        smoothGradient = cfg.level.gradientBetweenLevels;
        chromaAtSixHundred = cfg.level.chromaFor600Plus;
        chromaSpeed = clamp(cfg.level.chromaSpeed, 0.1f, 4.0f);
        bracketInput = normalHex(cfg.level.bracketColorHex);
        selectedThreshold = workingColors.firstKey();
        selectThreshold(selectedThreshold, true);
        dirty = false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        backdrop(graphics,delta);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        renderEditor(graphics, mouseX, mouseY);
    }

    private void renderEditor(Object graphics, int mouseX, int mouseY) {
        updateLayout();
        if (toastTicks > 0) {
            toastTicks--;
        } else {
            toast = "";
        }

        fillRect(graphics, 0, 0, width, height, 0xA9000000);
        drawShadow(graphics, px, py, panelW, panelH, 9);
        fillRoundRect(graphics, px, py, panelW, panelH, 9, C_PANEL);
        drawRoundBorder(graphics, px, py, panelW, panelH, 9, C_BORDER);

        drawHeader(graphics, mouseX, mouseY);
        drawThresholdList(graphics, mouseX, mouseY);
        drawColorEditor(graphics, mouseX, mouseY);
        drawFooter(graphics, mouseX, mouseY);
    }

    private void drawHeader(Object graphics, int mouseX, int mouseY) {
        fillRoundRect(graphics, px, py, panelW, HEADER_H, 9, 0xFF1B140F);
        fillRect(graphics, px + 9, py + HEADER_H - 1, panelW - 18, 1, C_BORDER);

        int iconX = px + 14;
        fillRoundRect(graphics, iconX, py + 11, 28, 28, 8, C_ACTIVE);
        drawRoundBorder(graphics, iconX, py + 11, 28, 28, 8, C_BORDER_ACT);

        drawString(graphics, "12", iconX + 6, py + 21, C_GOLD);
        drawString(graphics, "LEVEL COLORS", iconX + 38, py + 14, C_TEXT);
        drawString(graphics, "Build your own SkyBlock prestige palette", iconX + 38, py + 28, C_TEXT_DIM);

        int closeX = px + panelW - 34;
        boolean hover = inside(mouseX, mouseY, closeX, py + 15, 20, 20);
        fillRoundRect(graphics, closeX, py + 15, 20, 20, 6, hover ? C_ACTIVE : C_SURFACE);
        drawRoundBorder(graphics, closeX, py + 15, 20, 20, 6, hover ? C_GOLD : C_BORDER);
        drawCenteredString(graphics, "X", closeX + 10, py + 21, hover ? C_GOLD : C_TEXT_DIM);
    }

    private void drawThresholdList(Object graphics, int mouseX, int mouseY) {
        int x = listX();
        int y = contentTop();
        int w = listW();
        int h = contentBottom() - y;
        fillRoundRect(graphics, x, y, w, h, 8, C_SURFACE);
        drawRoundBorder(graphics, x, y, w, h, 8, C_BORDER);

        drawString(graphics, "THRESHOLDS", x + 11, y + 10, C_GOLD_DARK);
        String count = workingColors.size() + " colors";
        drawString(graphics, count, x + w - getStringWidth(count) - 10, y + 10, C_TEXT_DIM);
        fillRect(graphics, x + 9, y + 27, w - 18, 1, C_BORDER);

        int viewY = listViewY();
        int viewH = listViewH();
        int rowY = viewY - listScroll;
        for (Map.Entry<Integer, String> entry : workingColors.entrySet()) {
            if (rowY + LIST_ROW_H > viewY && rowY < viewY + viewH) {
                drawThresholdRow(graphics, mouseX, mouseY, x + 7, rowY, w - 14, entry);
            }
            rowY += LIST_ROW_H + 3;
        }
        drawListScrollbar(graphics);

        int buttonY = listButtonY();
        int gap = 5;
        int addW = Math.max(45, (w - 20 - gap) / 2);
        drawButton(graphics, mouseX, mouseY, x + 8, buttonY, addW, 23, "+ Add", true);
        drawButton(graphics, mouseX, mouseY, x + 8 + addW + gap, buttonY,
                w - 16 - addW - gap, 23, "Delete", false);
    }

    private void drawThresholdRow(Object graphics, int mouseX, int mouseY, int x, int y, int w,
                                  Map.Entry<Integer, String> entry) {
        boolean selected = entry.getKey() == selectedThreshold;
        boolean hover = inside(mouseX, mouseY, x, y, w, LIST_ROW_H);
        fillRoundRect(graphics, x, y, w, LIST_ROW_H, 6,
                selected ? C_ACTIVE : (hover ? C_HOVER : C_INPUT));
        drawRoundBorder(graphics, x, y, w, LIST_ROW_H, 6, selected ? C_GOLD : C_BORDER);
        int color = colorOf(entry.getValue());
        fillRoundRect(graphics, x + 7, y + 7, 14, 14, 4, color);
        drawRoundBorder(graphics, x + 7, y + 7, 14, 14, 4, 0x88FFFFFF);
        drawString(graphics, "Lv. " + entry.getKey(), x + 28, y + 10, selected ? C_TEXT : C_TEXT_DIM);
        String hex = entry.getValue();
        drawString(graphics, hex, x + w - getStringWidth(hex) - 8, y + 10, color);
    }

    private void drawColorEditor(Object graphics, int mouseX, int mouseY) {
        int x = editorX();
        int y = contentTop();
        int w = editorW();
        int h = contentBottom() - y;
        fillRoundRect(graphics, x, y, w, h, 8, C_SURFACE);
        drawRoundBorder(graphics, x, y, w, h, 8, C_BORDER);

        drawString(graphics, "COLOR EDITOR", x + 12, y + 10, C_GOLD_DARK);
        String selected = "Level " + selectedThreshold;
        drawString(graphics, selected, x + w - getStringWidth(selected) - 12, y + 10, C_TEXT_DIM);

        int inputY = y + 28;
        int thresholdW = Math.max(70, Math.min(104, (w - 38) / 3));
        int hexW = Math.max(84, Math.min(116, (w - 38) / 3));
        drawInput(graphics, mouseX, mouseY, x + 12, inputY, thresholdW, "LEVEL", thresholdInput, Field.THRESHOLD);
        drawInput(graphics, mouseX, mouseY, x + 19 + thresholdW, inputY, hexW, "HEX", hexInput, Field.HEX);

        int swatchX = x + 26 + thresholdW + hexW;
        int swatchW = Math.max(45, x + w - 12 - swatchX);
        int selectedColor = hsvToRgb(hue, saturation, value);
        fillRoundRect(graphics, swatchX, inputY + 11, swatchW, 23, 6, withAlpha(selectedColor));
        drawRoundBorder(graphics, swatchX, inputY + 11, swatchW, 23, 6, 0x99FFFFFF);
        if (swatchW >= 68) {
            drawCenteredString(graphics, "LIVE", swatchX + swatchW / 2, inputY + 19,
                    contrastText(selectedColor));
        }

        int pickerX = x + 12;
        int pickerY = inputY + 47;
        int pickerW = pickerW();
        int pickerH = pickerH();
        drawSaturationValuePicker(graphics, pickerX, pickerY, pickerW, pickerH);
        drawHueBar(graphics, pickerX, hueY(), pickerW);
        drawPickerMarker(graphics, pickerX, pickerY, pickerW, pickerH);

        int previewX = pickerX + pickerW + 12;
        int previewW = x + w - 12 - previewX;
        if (previewW >= 92) {
            drawPreviewCard(graphics, previewX, pickerY, previewW, pickerH + HUE_H + 10, selectedColor);
        }

        int rgbY = hueY() + HUE_H + 10;
        int channelGap = 5;
        int channelW = Math.max(38, (pickerW - channelGap * 2) / 3);
        drawInput(graphics, mouseX, mouseY, pickerX, rgbY, channelW, "R", redInput, Field.RED);
        drawInput(graphics, mouseX, mouseY, pickerX + channelW + channelGap, rgbY, channelW, "G", greenInput, Field.GREEN);
        drawInput(graphics, mouseX, mouseY, pickerX + (channelW + channelGap) * 2, rgbY, channelW, "B", blueInput, Field.BLUE);

        String hsvText = "H " + Math.round(hue * 360) + "°   S " + Math.round(saturation * 100)
                + "%   V " + Math.round(value * 100) + "%";
        int hsvX = previewW >= 92 ? previewX : pickerX + pickerW + 8;
        int hsvW = previewW >= 92 ? previewW : Math.max(0, x + w - 12 - hsvX);
        if (hsvW >= getStringWidth("H 000°")) {
            drawString(graphics, fitting(hsvText, hsvW), hsvX, rgbY + 20, C_TEXT_DIM);
        }

        int optionsY = rgbY + 39;
        drawEffectControls(graphics, mouseX, mouseY, optionsY);
    }

    private void drawSaturationValuePicker(Object graphics, int x, int y, int w, int h) {
        saturationValue(graphics,x,y,w,h,hue);
        drawRoundBorder(graphics, x, y, w, h, 4, C_BORDER_ACT);
    }

    private void drawHueBar(Object graphics, int x, int y, int w) {
        hueStrip(graphics,x,y,w,HUE_H);
        drawRoundBorder(graphics, x, y, w, HUE_H, 4, C_BORDER_ACT);
        int markerX = x + Math.round(hue * (w - 1));
        fillRect(graphics, markerX - 1, y - 2, 3, HUE_H + 4, 0xFF0A0806);
        fillRect(graphics, markerX, y - 1, 1, HUE_H + 2, C_TEXT);
    }

    private void drawPickerMarker(Object graphics, int x, int y, int w, int h) {
        int markerX = x + Math.round(saturation * (w - 1));
        int markerY = y + Math.round((1.0f - value) * (h - 1));
        fillRoundRect(graphics, markerX - 4, markerY - 4, 9, 9, 4, 0xFF0A0806);
        fillRoundRect(graphics, markerX - 3, markerY - 3, 7, 7, 3, 0xFFF7EEE5);
        fillRoundRect(graphics, markerX - 1, markerY - 1, 3, 3, 1, withAlpha(hsvToRgb(hue, saturation, value)));
    }

    private void drawPreviewCard(Object graphics, int x, int y, int w, int h, int currentColor) {
        fillRoundRect(graphics, x, y, w, h, 6, C_INPUT);
        drawRoundBorder(graphics, x, y, w, h, 6, C_BORDER);
        drawString(graphics, "LIVE PREVIEW", x + 9, y + 9, C_GOLD_DARK);
        int bracket = colorOf(bracketInput);
        int textY = y + 29;
        if(graphics instanceof GuiGraphicsExtractor ge){
            var sample=Component.literal("[").withColor(bracket&0xFFFFFF)
                    .append(Component.literal(String.valueOf(selectedThreshold)).withColor(currentColor&0xFFFFFF))
                    .append(Component.literal("]").withColor(bracket&0xFFFFFF))
                    .append(Component.literal(" PlayerName").withColor(C_TEXT&0xFFFFFF));
            ge.text(Minecraft.getInstance().font,sample,x+10,textY,-1,false);
        }

        drawString(graphics, "Current", x + 9, y + 50, C_TEXT_DIM);
        fillRoundRect(graphics, x + 9, y + 64, w - 18, 14, 4, withAlpha(currentColor));
        drawRoundBorder(graphics, x + 9, y + 64, w - 18, 14, 4, 0x66FFFFFF);
        String text = hexFor(currentColor);
        drawCenteredString(graphics, text, x + w / 2, y + 67, contrastText(currentColor));

        if (h >= 122) {
            drawString(graphics, smoothGradient ? "Smooth transition" : "Hard thresholds", x + 9, y + 91,
                    smoothGradient ? C_GOLD : C_TEXT_DIM);
            drawString(graphics, chromaAtSixHundred ? "Chroma at 600+" : "Static 600+", x + 9, y + 105,
                    chromaAtSixHundred ? C_GOLD : C_TEXT_DIM);
        }
    }

    private void drawEffectControls(Object graphics, int mouseX, int mouseY, int y) {
        int x = editorX() + 12;
        int w = editorW() - 24;
        int gap = 6;
        int cellW = Math.max(84, (w - gap) / 2);
        drawToggleCard(graphics, mouseX, mouseY, x, y, cellW, "Custom colors", levelEnabled);
        drawToggleCard(graphics, mouseX, mouseY, x + cellW + gap, y, w - cellW - gap,
                "Override Hypixel", overrideHypixelColors);
        drawToggleCard(graphics, mouseX, mouseY, x, y + 27, cellW, "Smooth gradient", smoothGradient);
        drawToggleCard(graphics, mouseX, mouseY, x + cellW + gap, y + 27, w - cellW - gap,
                "Chroma 600+", chromaAtSixHundred);

        int speedY = y + 59;
        drawString(graphics, "CHROMA SPEED", x, speedY + 7, C_GOLD_DARK);
        int minusX = x + 88;
        drawSquareButton(graphics, mouseX, mouseY, minusX, speedY, 19, "-");
        String speed = String.format(java.util.Locale.ROOT, "%.1fx", chromaSpeed);
        drawString(graphics, speed, minusX + 25, speedY + 6, C_TEXT);
        drawSquareButton(graphics, mouseX, mouseY, minusX + 66, speedY, 19, "+");

        int bracketX = Math.min(x + w - 100, minusX + 95);
        if (bracketX > minusX + 70) {
            drawInput(graphics, mouseX, mouseY, bracketX, speedY - 5, x + w - bracketX,
                    "[ ]", bracketInput, Field.BRACKET);
        }

        int paletteY = speedY + 31;
        if (paletteY + 20 < contentBottom()) {
            drawString(graphics, "PALETTE PREVIEW", x, paletteY + 4, C_GOLD_DARK);
            drawPalettePreview(graphics, x, paletteY + 12, w, 12);
        }
    }

    private void drawToggleCard(Object graphics, int mouseX, int mouseY, int x, int y, int w,
                                String label, boolean enabled) {
        boolean hover = inside(mouseX, mouseY, x, y, w, 21);
        fillRoundRect(graphics, x, y, w, 21, 6, enabled ? C_ACTIVE : (hover ? C_HOVER : C_INPUT));
        drawRoundBorder(graphics, x, y, w, 21, 6, enabled ? C_GOLD_DARK : C_BORDER);
        int textColor = enabled ? C_TEXT : C_TEXT_DIM;
        drawString(graphics, fitting(label, Math.max(24, w - 30)), x + 7, y + 7, textColor);
        int trackX = x + w - 21;
        fillRoundRect(graphics, trackX, y + 5, 14, 11, 6, enabled ? C_GOLD_DARK : 0xFF46382E);
        fillRoundRect(graphics, trackX + (enabled ? 6 : 1), y + 6, 9, 9, 5,
                enabled ? 0xFFFFE8D0 : 0xFF806B5A);
    }

    private void drawPalettePreview(Object graphics, int x, int y, int w, int h) {
        int samples = Math.max(8, Math.min(24, w / 15));
        int maxLevel = Math.max(700, workingColors.lastKey());
        for (int i = 0; i < samples; i++) {
            float fraction = samples == 1 ? 0 : (float) i / (samples - 1);
            int level = Math.round(maxLevel * fraction);
            int left = x + i * w / samples;
            int right = x + (i + 1) * w / samples;
            int color = previewColorForLevel(level);
            fillRect(graphics, left, y, Math.max(1, right - left), h, withAlpha(color));
        }
        drawRoundBorder(graphics, x, y, w, h, 3, 0x88FFFFFF);
    }

    private void drawInput(Object graphics, int mouseX, int mouseY, int x, int y, int w,
                           String label, String text, Field field) {
        if (w < 30) return;
        boolean active = activeField == field;
        boolean hover = inside(mouseX, mouseY, x, y, w, 37);
        drawString(graphics, label, x + 2, y, active ? C_GOLD : C_GOLD_DARK);
        fillRoundRect(graphics, x, y + 11, w, 23, 6, active ? C_INPUT_ACT : (hover ? C_HOVER : C_INPUT));
        drawRoundBorder(graphics, x, y + 11, w, 23, 6, active ? C_GOLD : C_BORDER);
        String shown = text == null ? "" : text;
        if (active && (System.currentTimeMillis() / 450) % 2 == 0) shown += "|";
        drawString(graphics, fitting(shown, w - 12), x + 6, y + 18, active ? C_TEXT : C_TEXT_DIM);
    }

    private void drawFooter(Object graphics, int mouseX, int mouseY) {
        int y = footerY();
        fillRoundRect(graphics, px, y, panelW, FOOTER_H, 9, C_PANEL);
        fillRect(graphics, px + 9, y, panelW - 18, 1, C_BORDER);

        String status = toast.isEmpty() ? (dirty ? "Unsaved edits" : "Palette ready") : toast;
        drawString(graphics, status, px + 14, y + 17, dirty && toast.isEmpty() ? C_GOLD : C_TEXT_DIM);

        int applyW = 76;
        int backW = 58;
        int applyX = px + panelW - 14 - applyW;
        drawButton(graphics, mouseX, mouseY, applyX - backW - 7, y + 9, backW, 24, "Back", false);
        drawButton(graphics, mouseX, mouseY, applyX, y + 9, applyW, 24, "Apply", true);
    }

    private void drawButton(Object graphics, int mouseX, int mouseY, int x, int y, int w, int h,
                            String text, boolean primary) {
        boolean hover = inside(mouseX, mouseY, x, y, w, h);
        int fill = primary ? (hover ? 0xFFF0A15B : C_GOLD) : (hover ? C_HOVER : C_SURFACE);
        int textColor = primary ? 0xFF24170E : (hover ? C_TEXT : C_TEXT_DIM);
        fillRoundRect(graphics, x, y, w, h, 6, fill);
        drawRoundBorder(graphics, x, y, w, h, 6, primary ? C_GOLD : C_BORDER);
        drawCenteredString(graphics, text, x + w / 2, y + (h - 8) / 2, textColor);
    }

    private void drawSquareButton(Object graphics, int mouseX, int mouseY, int x, int y, int size, String text) {
        boolean hover = inside(mouseX, mouseY, x, y, size, size);
        fillRoundRect(graphics, x, y, size, size, 5, hover ? C_HOVER : C_INPUT);
        drawRoundBorder(graphics, x, y, size, size, 5, hover ? C_GOLD : C_BORDER);
        drawCenteredString(graphics, text, x + size / 2, y + 5, hover ? C_GOLD : C_TEXT);
    }

    private void drawListScrollbar(Object graphics) {
        int viewport = listViewH();
        int content = workingColors.size() * (LIST_ROW_H + 3) - 3;
        if (content <= viewport) return;
        int trackX = listX() + listW() - 5;
        int thumbH = Math.max(20, viewport * viewport / content);
        int maxScroll = Math.max(1, content - viewport);
        int thumbY = listViewY() + (viewport - thumbH) * listScroll / maxScroll;
        fillRoundRect(graphics, trackX, listViewY(), 2, viewport, 1, 0x443F2C1D);
        fillRoundRect(graphics, trackX, thumbY, 2, thumbH, 1, C_GOLD_DARK);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean ignored) {
        if (event != null && handleClick((int) event.x(), (int) event.y(), event.button())) return true;
        return super.mouseClicked(event, ignored);
    }

    private boolean handleClick(int mouseX, int mouseY, int button) {
        updateLayout();
        if (button != 0) return false;
        int closeX = px + panelW - 34;
        if (inside(mouseX, mouseY, closeX, py + 15, 20, 20)) {
            onClose();
            return true;
        }

        int applyW = 76;
        int backW = 58;
        int applyX = px + panelW - 14 - applyW;
        if (inside(mouseX, mouseY, applyX, footerY() + 9, applyW, 24)) {
            applyWorkingCopy();
            return true;
        }
        if (inside(mouseX, mouseY, applyX - backW - 7, footerY() + 9, backW, 24)) {
            onClose();
            return true;
        }

        if (inside(mouseX, mouseY, listX(), contentTop(), listW(), contentBottom() - contentTop())) {
            return handleListClick(mouseX, mouseY);
        }
        if (inside(mouseX, mouseY, editorX(), contentTop(), editorW(), contentBottom() - contentTop())) {
            return handleEditorClick(mouseX, mouseY);
        }
        activeField = Field.NONE;
        return false;
    }

    private boolean handleListClick(int mouseX, int mouseY) {
        if (!commitActiveField()) return true;
        int x = listX();
        int w = listW();
        int buttonY = listButtonY();
        int addW = Math.max(45, (w - 20 - 5) / 2);
        if (inside(mouseX, mouseY, x + 8, buttonY, addW, 23)) {
            addThreshold();
            return true;
        }
        if (inside(mouseX, mouseY, x + 8 + addW + 5, buttonY, w - 16 - addW - 5, 23)) {
            deleteSelectedThreshold();
            return true;
        }

        int rowY = listViewY() - listScroll;
        for (Integer threshold : workingColors.keySet()) {
            if (inside(mouseX, mouseY, x + 7, rowY, w - 14, LIST_ROW_H)) {
                selectThreshold(threshold, false);
                return true;
            }
            rowY += LIST_ROW_H + 3;
        }
        return true;
    }

    private boolean handleEditorClick(int mouseX, int mouseY) {
        int x = editorX();
        int y = contentTop();
        int w = editorW();
        int inputY = y + 28;
        int thresholdW = Math.max(70, Math.min(104, (w - 38) / 3));
        int hexW = Math.max(84, Math.min(116, (w - 38) / 3));
        if (inputHit(mouseX, mouseY, x + 12, inputY, thresholdW)) return focus(Field.THRESHOLD);
        if (inputHit(mouseX, mouseY, x + 19 + thresholdW, inputY, hexW)) return focus(Field.HEX);

        int pickerX = x + 12;
        int pickerY = inputY + 47;
        if (inside(mouseX, mouseY, pickerX, pickerY, pickerW(), pickerH())) {
            if (!commitActiveField()) return true;
            dragTarget = DragTarget.SATURATION_VALUE;
            applySaturationValue(mouseX, mouseY);
            return true;
        }
        if (inside(mouseX, mouseY, pickerX, hueY(), pickerW(), HUE_H)) {
            if (!commitActiveField()) return true;
            dragTarget = DragTarget.HUE;
            applyHue(mouseX);
            return true;
        }

        int rgbY = hueY() + HUE_H + 10;
        int channelW = Math.max(38, (pickerW() - 10) / 3);
        if (inputHit(mouseX, mouseY, pickerX, rgbY, channelW)) return focus(Field.RED);
        if (inputHit(mouseX, mouseY, pickerX + channelW + 5, rgbY, channelW)) return focus(Field.GREEN);
        if (inputHit(mouseX, mouseY, pickerX + (channelW + 5) * 2, rgbY, channelW)) return focus(Field.BLUE);

        int optionsY = rgbY + 39;
        int cellW = Math.max(84, (editorW() - 24 - 6) / 2);
        int secondX = editorX() + 12 + cellW + 6;
        int toggleW2 = editorX() + editorW() - 12 - secondX;
        if (inside(mouseX, mouseY, editorX() + 12, optionsY, cellW, 21)) {
            if (!commitActiveField()) return true;
            levelEnabled = !levelEnabled;
            dirty = true;
            return true;
        }
        if (inside(mouseX, mouseY, secondX, optionsY, toggleW2, 21)) {
            if (!commitActiveField()) return true;
            overrideHypixelColors = !overrideHypixelColors;
            dirty = true;
            return true;
        }
        if (inside(mouseX, mouseY, editorX() + 12, optionsY + 27, cellW, 21)) {
            if (!commitActiveField()) return true;
            smoothGradient = !smoothGradient;
            dirty = true;
            return true;
        }
        if (inside(mouseX, mouseY, secondX, optionsY + 27, toggleW2, 21)) {
            if (!commitActiveField()) return true;
            chromaAtSixHundred = !chromaAtSixHundred;
            dirty = true;
            return true;
        }

        int speedY = optionsY + 59;
        int minusX = editorX() + 12 + 88;
        if (inside(mouseX, mouseY, minusX, speedY, 19, 19)) {
            if (!commitActiveField()) return true;
            chromaSpeed = clamp(chromaSpeed - 0.1f, 0.1f, 4.0f);
            dirty = true;
            return true;
        }
        if (inside(mouseX, mouseY, minusX + 66, speedY, 19, 19)) {
            if (!commitActiveField()) return true;
            chromaSpeed = clamp(chromaSpeed + 0.1f, 0.1f, 4.0f);
            dirty = true;
            return true;
        }
        int bracketX = Math.min(editorX() + 12 + editorW() - 100, minusX + 95);
        if (bracketX > minusX + 70 && inputHit(mouseX, mouseY, bracketX, speedY - 5,
                editorX() + editorW() - 12 - bracketX)) {
            return focus(Field.BRACKET);
        }

        activeField = Field.NONE;
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (event != null && event.button() == 0 && dragTarget != DragTarget.NONE) {
            if (dragTarget == DragTarget.SATURATION_VALUE) applySaturationValue((int) event.x(), (int) event.y());
            if (dragTarget == DragTarget.HUE) applyHue((int) event.x());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        dragTarget = DragTarget.NONE;
        if (event != null) return true;
        return super.mouseReleased(null);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (inside((int) mouseX, (int) mouseY, listX(), listViewY(), listW(), listViewH())) {
            int content = Math.max(0, workingColors.size() * (LIST_ROW_H + 3) - 3);
            int maxScroll = Math.max(0, content - listViewH());
            listScroll = clamp((int) (listScroll - verticalAmount * 22), 0, maxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if(event!=null && (event.modifiers()&2)!=0 && (event.key()==90 || event.key()==89)) return super.keyPressed(event);
        if (event != null && handleKey(event.key())) return true;
        return super.keyPressed(event);
    }

    private boolean handleKey(int key) {
        if (activeField != Field.NONE) {
            if (key == 257 || key == 335) {
                return commitActiveField();
            }
            if (key == 259) {
                setActiveText(trimLast(activeText()));
                dirty = true;
                return true;
            }
            if (key == 256) {
                activeField = Field.NONE;
                return true;
            }
            return true;
        }
        if (key == 256) {
            onClose();
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (event != null && handleCharacter((char) event.codepoint())) return true;
        return super.charTyped(event);
    }

    private boolean handleCharacter(char character) {
        if (activeField == Field.NONE || character < 32) return false;
        String current = activeText();
        if (!isAllowedCharacter(activeField, character) || current.length() >= maxLength(activeField)) return true;
        setActiveText(current + character);
        dirty = true;
        return true;
    }

    private boolean focus(Field field) {
        if (activeField != field && !commitActiveField()) return true;
        activeField = field;
        return true;
    }

    private boolean commitActiveField() {
        Field field = activeField;
        if (field == Field.NONE) return true;
        boolean valid = switch (field) {
            case THRESHOLD -> commitThreshold();
            case HEX -> commitHex();
            case RED, GREEN, BLUE -> commitRgb();
            case BRACKET -> commitBracket();
            case NONE -> true;
        };
        if (valid) activeField = Field.NONE;
        return valid;
    }

    private boolean commitThreshold() {
        try {
            int requested = Integer.parseInt(thresholdInput.trim());
            if (requested < 0 || requested > 9999) throw new NumberFormatException();
            if (requested != selectedThreshold && workingColors.containsKey(requested)) {
                toast("That level already has a threshold");
                return false;
            }
            if (requested != selectedThreshold) {
                String color = workingColors.remove(selectedThreshold);
                workingColors.put(requested, color == null ? hexFor(hsvToRgb(hue, saturation, value)) : color);
                selectedThreshold = requested;
                dirty = true;
            }
            thresholdInput = String.valueOf(selectedThreshold);
            return true;
        } catch (NumberFormatException ignored) {
            toast("Level must be a number from 0 to 9999");
            return false;
        }
    }

    private boolean commitHex() {
        if (!isHex(hexInput)) {
            toast("Use a six-digit color, for example #FFAA00");
            return false;
        }
        setSelectedColor(colorOf(hexInput));
        return true;
    }

    private boolean commitRgb() {
        try {
            int r = parseChannel(redInput);
            int g = parseChannel(greenInput);
            int b = parseChannel(blueInput);
            setSelectedColor((r << 16) | (g << 8) | b);
            return true;
        } catch (NumberFormatException ignored) {
            toast("RGB values must be between 0 and 255");
            return false;
        }
    }

    private boolean commitBracket() {
        if (!isHex(bracketInput)) {
            toast("Bracket color must look like #555555");
            return false;
        }
        bracketInput = normalHex(bracketInput);
        dirty = true;
        return true;
    }

    private void addThreshold() {
        int candidate = selectedThreshold + 10;
        while (candidate <= 9999 && workingColors.containsKey(candidate)) candidate += 10;
        if (candidate > 9999) {
            candidate = 0;
            while (candidate <= 9999 && workingColors.containsKey(candidate)) candidate++;
        }
        if (candidate > 9999) {
            toast("No free level threshold remains");
            return;
        }
        workingColors.put(candidate, hexFor(hsvToRgb(hue, saturation, value)));
        selectThreshold(candidate, true);
        dirty = true;
        toast("Added level " + candidate);
    }

    private void deleteSelectedThreshold() {
        if (workingColors.size() <= 1) {
            toast("Keep at least one color threshold");
            return;
        }
        Integer replacement = workingColors.lowerKey(selectedThreshold);
        if (replacement == null) replacement = workingColors.higherKey(selectedThreshold);
        workingColors.remove(selectedThreshold);
        selectThreshold(replacement == null ? workingColors.firstKey() : replacement, true);
        dirty = true;
        toast("Threshold removed");
    }

    private void selectThreshold(int threshold, boolean force) {
        if (!force && !commitActiveField()) return;
        selectedThreshold = threshold;
        String color = workingColors.get(threshold);
        if (color == null) color = "#FFFFFF";
        int rgb = colorOf(color);
        float[] hsv = rgbToHsv(rgb);
        hue = hsv[0];
        saturation = hsv[1];
        value = hsv[2];
        syncColorInputs(rgb);
        thresholdInput = String.valueOf(threshold);
        ensureSelectedVisible();
    }

    private void setSelectedColor(int rgb) {
        int clean = rgb & 0xFFFFFF;
        workingColors.put(selectedThreshold, hexFor(clean));
        float[] hsv = rgbToHsv(clean);
        hue = hsv[0];
        saturation = hsv[1];
        value = hsv[2];
        syncColorInputs(clean);
        dirty = true;
    }

    private void syncColorInputs(int rgb) {
        int clean = rgb & 0xFFFFFF;
        hexInput = hexFor(clean);
        redInput = String.valueOf((clean >> 16) & 0xFF);
        greenInput = String.valueOf((clean >> 8) & 0xFF);
        blueInput = String.valueOf(clean & 0xFF);
    }

    private void applySaturationValue(int mouseX, int mouseY) {
        int x = editorX() + 12;
        int y = contentTop() + 28 + 47;
        saturation = clamp((mouseX - x) / (float) Math.max(1, pickerW() - 1), 0.0f, 1.0f);
        value = clamp(1.0f - (mouseY - y) / (float) Math.max(1, pickerH() - 1), 0.0f, 1.0f);
        setSelectedColor(hsvToRgb(hue, saturation, value));
    }

    private void applyHue(int mouseX) {
        int x = editorX() + 12;
        hue = clamp((mouseX - x) / (float) Math.max(1, pickerW() - 1), 0.0f, 1.0f);
        setSelectedColor(hsvToRgb(hue, saturation, value));
    }

    private void applyWorkingCopy() {
        if (!commitActiveField()) return;
        if (workingColors.isEmpty()) {
            toast("Add at least one level color first");
            return;
        }
        cfg.level.customColors = new TreeMap<>(workingColors);
        cfg.level.enabled = levelEnabled;
        cfg.level.overrideHypixelColors = overrideHypixelColors;
        cfg.level.gradientBetweenLevels = smoothGradient;
        cfg.level.chromaFor600Plus = chromaAtSixHundred;
        cfg.level.chromaSpeed = chromaSpeed;
        cfg.level.bracketColorHex = normalHex(bracketInput);
        cfg.save();
        dirty = false;
        toast("Applied and saved");
    }

    @Override
    public void onClose() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void normalizeSelection() {
        if (workingColors.isEmpty()) workingColors.putAll(LevelColorUtil.getDefaultExtendedHexMap());
        if (!workingColors.containsKey(selectedThreshold)) selectedThreshold = workingColors.firstKey();
        selectThreshold(selectedThreshold, true);
    }

    private void ensureSelectedVisible() {
        int index = 0;
        for (int threshold : workingColors.keySet()) {
            if (threshold == selectedThreshold) break;
            index++;
        }
        int top = index * (LIST_ROW_H + 3);
        int bottom = top + LIST_ROW_H;
        if (top < listScroll) listScroll = top;
        if (bottom > listScroll + listViewH()) listScroll = Math.max(0, bottom - listViewH());
        int content = Math.max(0, workingColors.size() * (LIST_ROW_H + 3) - 3);
        listScroll = clamp(listScroll, 0, Math.max(0, content - listViewH()));
    }

    private String activeText() {
        return switch (activeField) {
            case THRESHOLD -> thresholdInput;
            case HEX -> hexInput;
            case RED -> redInput;
            case GREEN -> greenInput;
            case BLUE -> blueInput;
            case BRACKET -> bracketInput;
            case NONE -> "";
        };
    }

    private void setActiveText(String text) {
        switch (activeField) {
            case THRESHOLD -> thresholdInput = text;
            case HEX -> hexInput = text;
            case RED -> redInput = text;
            case GREEN -> greenInput = text;
            case BLUE -> blueInput = text;
            case BRACKET -> bracketInput = text;
            case NONE -> { }
        }
    }

    private static boolean isAllowedCharacter(Field field, char character) {
        return switch (field) {
            case HEX, BRACKET -> character == '#' || (character >= '0' && character <= '9')
                    || (character >= 'a' && character <= 'f') || (character >= 'A' && character <= 'F');
            case THRESHOLD, RED, GREEN, BLUE -> character >= '0' && character <= '9';
            case NONE -> false;
        };
    }

    private static int maxLength(Field field) {
        return switch (field) {
            case HEX, BRACKET -> 7;
            case THRESHOLD -> 4;
            case RED, GREEN, BLUE -> 3;
            case NONE -> 0;
        };
    }

    private void toast(String message) {
        toast = message;
        toastTicks = 90;
    }

    private void updateLayout() {
        panelW = Math.min(MAX_PANEL_W, Math.max(340, width - 16));
        panelH = Math.min(MAX_PANEL_H, Math.max(310, height - 16));
        px = (width - panelW) / 2;
        py = (height - panelH) / 2;
    }

    private int contentTop() { return py + HEADER_H + 8; }
    private int contentBottom() { return footerY() - 6; }
    private int footerY() { return py + panelH - FOOTER_H; }
    private int listX() { return px + 11; }
    private int listW() { return Math.max(118, Math.min(190, (panelW - 38) / 3)); }
    private int editorX() { return listX() + listW() + 10; }
    private int editorW() { return px + panelW - 11 - editorX(); }
    private int listViewY() { return contentTop() + 36; }
    private int listButtonY() { return contentBottom() - 31; }
    private int listViewH() { return Math.max(30, listButtonY() - listViewY() - 5); }
    private int pickerW() { return Math.max(112, Math.min(250, editorW() - 24)); }
    private int pickerH() {
        int available = contentBottom() - (contentTop() + 28 + 47) - 144;
        return clamp(available, 78, 150);
    }
    private int hueY() { return contentTop() + 28 + 47 + pickerH() + 9; }

    private static boolean inside(int mouseX, int mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private static boolean inputHit(int mouseX, int mouseY, int x, int y, int w) {
        return inside(mouseX, mouseY, x, y, w, 37);
    }

    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
    private static float clamp(float value, float min, float max) { return Math.max(min, Math.min(max, value)); }

    private static int parseChannel(String value) {
        int channel = Integer.parseInt(value.trim());
        if (channel < 0 || channel > 255) throw new NumberFormatException();
        return channel;
    }

    private static String trimLast(String text) {
        return text == null || text.isEmpty() ? "" : text.substring(0, text.length() - 1);
    }

    private static boolean isHex(String value) {
        return value != null && value.trim().matches("(?i)#?[0-9a-f]{6}");
    }

    private static String normalHex(String value) {
        if (!isHex(value)) return "#FFFFFF";
        String clean = value.trim().replace("#", "").toUpperCase(java.util.Locale.ROOT);
        return "#" + clean;
    }

    private static int colorOf(String value) {
        try {
            return Integer.parseInt(normalHex(value).substring(1), 16) & 0xFFFFFF;
        } catch (Exception ignored) {
            return 0xFFFFFF;
        }
    }

    private static String hexFor(int rgb) {
        return String.format(java.util.Locale.ROOT, "#%06X", rgb & 0xFFFFFF);
    }

    private static int withAlpha(int rgb) { return 0xFF000000 | (rgb & 0xFFFFFF); }

    private static int contrastText(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        return (r * 299 + g * 587 + b * 114) / 1000 > 145 ? 0xFF17110D : C_TEXT;
    }

    private static int hsvToRgb(float h, float s, float v) {
        h = h - (float) Math.floor(h);
        s = clamp(s, 0.0f, 1.0f);
        v = clamp(v, 0.0f, 1.0f);
        float c = v * s;
        float x = c * (1.0f - Math.abs((h * 6.0f) % 2.0f - 1.0f));
        float m = v - c;
        float r;
        float g;
        float b;
        int sector = (int) (h * 6.0f);
        switch (sector) {
            case 0 -> { r = c; g = x; b = 0; }
            case 1 -> { r = x; g = c; b = 0; }
            case 2 -> { r = 0; g = c; b = x; }
            case 3 -> { r = 0; g = x; b = c; }
            case 4 -> { r = x; g = 0; b = c; }
            default -> { r = c; g = 0; b = x; }
        }
        return (Math.round((r + m) * 255) << 16)
                | (Math.round((g + m) * 255) << 8)
                | Math.round((b + m) * 255);
    }

    private static float[] rgbToHsv(int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255.0f;
        float g = ((rgb >> 8) & 0xFF) / 255.0f;
        float b = (rgb & 0xFF) / 255.0f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float delta = max - min;
        float h;
        if (delta == 0) h = 0;
        else if (max == r) h = ((g - b) / delta + (g < b ? 6 : 0)) / 6.0f;
        else if (max == g) h = ((b - r) / delta + 2) / 6.0f;
        else h = ((r - g) / delta + 4) / 6.0f;
        float s = max == 0 ? 0 : delta / max;
        return new float[]{h, s, max};
    }

    private int previewColorForLevel(int level) {
        if (chromaAtSixHundred && level >= 600) {
            float movingHue = ((System.currentTimeMillis() % 4000L) / 4000.0f + level / 420.0f) % 1.0f;
            return hsvToRgb(movingHue, 0.88f, 1.0f);
        }
        Map.Entry<Integer, String> lower = workingColors.floorEntry(level);
        if (lower == null) return colorOf(workingColors.firstEntry().getValue());
        Map.Entry<Integer, String> upper = workingColors.ceilingEntry(level);
        if (!smoothGradient || upper == null || upper.getKey().equals(lower.getKey())) return colorOf(lower.getValue());
        float progress = (level - lower.getKey()) / (float) (upper.getKey() - lower.getKey());
        return lerpRgb(colorOf(lower.getValue()), colorOf(upper.getValue()), progress);
    }

    private static int lerpRgb(int a, int b, float t) {
        t = clamp(t, 0.0f, 1.0f);
        int r = Math.round(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = Math.round(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int blue = Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return (r << 16) | (g << 8) | blue;
    }

    private static String fitting(String text, int maxWidth) {
        if (text == null) return "";
        if (getStringWidth(text) <= maxWidth) return text;
        String shortened = text;
        while (shortened.length() > 1 && getStringWidth(shortened + "...") > maxWidth) {
            shortened = shortened.substring(0, shortened.length() - 1);
        }
        return shortened + "...";
    }
}
