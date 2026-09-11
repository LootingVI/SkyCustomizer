package de.flori.skycustomizer.config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import java.awt.Color;
import java.util.Locale;
import java.util.function.Consumer;
import static de.flori.skycustomizer.render.ModernRenderHelper.*;

public final class ColorPickerScreen extends StudioScreen {
    private final Screen parent;
    private final Consumer<String> onApply;
    private String color;
    private float hue, saturation, value;
    private EditBox hex;
    private int px, py, pw, ph, drag;
    private boolean syncing;
    @Override protected String[] historyFields(){return new String[]{"color","hue","saturation","value"};}
    @Override protected void afterHistory(){syncing=true;hex.setValue(color);syncing=false;}

    public ColorPickerScreen(Screen parent, String color, Consumer<String> onApply) {
        super("Color Picker");
        this.parent = parent; this.onApply = onApply;
        this.color = FeatureEditorScreen.normalizeColor(color);
        if (this.color == null) this.color = "#FFAA00";
        readColor(this.color);
    }
    @Override protected void init() {
        clearWidgets();
        pw = Math.min(340, width - 16); ph = Math.min(300, height - 16);
        px = (width - pw) / 2; py = (height - ph) / 2;
        hex = new EditBox(font, px + 23, py + ph - 75, 120, 18, Component.literal("Hex color"));
        hex.setBordered(false); hex.setTextShadow(false);hex.setMaxLength(7); hex.setValue(color);
        hex.setResponder(text -> {
            String normalized = FeatureEditorScreen.normalizeColor(text);
            if (!syncing && normalized != null) { color = normalized; readColor(color); }
        });
        addRenderableWidget(hex);
    }
    private void readColor(String hex) {
        int rgb = Integer.parseInt(hex.substring(1), 16);
        float[] hsv = Color.RGBtoHSB(rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255, null);
        hue = hsv[0]; saturation = hsv[1]; value = hsv[2];
    }
    private int pickerX() { return px + 16; }
    private int pickerY() { return py + 41; }
    private int pickerW() { return pw - 32; }
    private int pickerH() { return ph - 156; }
    private int hueY() { return py + ph - 107; }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        backdrop(g,delta);
        fillRect(g, 0, 0, width, height, 0xB0080604);
        fillRoundRect(g, px, py, pw, ph, 8, C_PANEL);
        drawRoundBorder(g, px, py, pw, ph, 8, C_BORDER);
        drawString(g, "Color Picker", px + 16, py + 13, C_TEXT);
        StudioWidgets.button(g,"Palettes",px+pw-90,py+6,74,mx,my,false);
        drawString(g, "Saturation / brightness", px + 16, py + 27, C_TEXT_DIM);
        int w = pickerW(), h = pickerH(), x = pickerX(), y = pickerY();
        saturationValue(g,x,y,w,h,hue);
        int markerX = x + Math.round(saturation * (w - 1));
        int markerY = y + Math.round((1 - value) * (h - 1));
        drawBorder(g, markerX - 3, markerY - 3, 7, 7, 0xFF000000);
        drawBorder(g, markerX - 2, markerY - 2, 5, 5, 0xFFFFFFFF);
        hueStrip(g,x,hueY(),w,14);
        fillRect(g, x + Math.round(hue * (w - 1)), hueY() - 2, 2, 18, C_TEXT);
        fillRoundRect(g, px + 16, py + ph - 83, 140, 26, 4, C_INPUT);
        hex.extractRenderState(g, mx, my, delta);
        int rgb = Integer.parseInt(color.substring(1), 16);
        fillRoundRect(g, px + pw - 75, py + ph - 83, 59, 26, 4, 0xFF000000 | rgb);
        String rgbText = "RGB " + (rgb >> 16 & 255) + " / " + (rgb >> 8 & 255) + " / " + (rgb & 255);
        drawString(g, rgbText, px + 17, py + ph - 47, C_TEXT_DIM);
        fillRoundRect(g, px + 16, py + ph - 31, 76, 23, 5, C_INPUT);
        drawCenteredString(g, "Cancel", px + 54, py + ph - 23, C_TEXT_DIM);
        fillRoundRect(g, px + pw - 92, py + ph - 31, 76, 23, 5, C_ACTIVE);
        drawCenteredString(g, "Apply color", px + pw - 54, py + ph - 23, C_GOLD);
    }
    @Override public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        if (e.button() == 0) {
            if(inside(e.x(),e.y(),px+pw-90,py+6,74,24)){
                minecraft.setScreen(new PaletteScreen(this,color,c->{color=c;readColor(c);hex.setValue(c);}));return true;
            }
            if (inside(e.x(), e.y(), px + 16, py + ph - 31, 76, 23)) { onClose(); return true; }
            if (inside(e.x(), e.y(), px + pw - 92, py + ph - 31, 76, 23)) {
                String normalized = FeatureEditorScreen.normalizeColor(hex.getValue());
                if (normalized != null) { PaletteStore.remember(normalized);onApply.accept(normalized); onClose(); }
                else hex.setTextColor(C_RED);
                return true;
            }
            if (inside(e.x(), e.y(), pickerX(), pickerY(), pickerW(), pickerH())) drag = 1;
            else if (inside(e.x(), e.y(), pickerX(), hueY(), pickerW(), 14)) drag = 2;
            if (drag != 0) { pick(e.x(), e.y()); return true; }
        }
        return super.mouseClicked(e, doubleClick);
    }
    private void pick(double x, double y) {
        float fraction = Math.clamp((float) (x - pickerX()) / (pickerW() - 1), 0, 1);
        if (drag == 2) hue = fraction;
        else {
            saturation = fraction;
            value = 1 - Math.clamp((float) (y - pickerY()) / (pickerH() - 1), 0, 1);
        }
        color = String.format(Locale.ROOT, "#%06X", Color.HSBtoRGB(hue, saturation, value) & 0xFFFFFF);
        syncing = true; hex.setValue(color); syncing = false; hex.setTextColor(C_TEXT);
    }
    @Override public boolean mouseDragged(MouseButtonEvent e, double x, double y) {
        if (e.button() == 0 && drag != 0) { pick(e.x(), e.y()); return true; }
        return super.mouseDragged(e, x, y);
    }
    @Override public boolean mouseReleased(MouseButtonEvent e) { drag = 0; return super.mouseReleased(e); }
    @Override public boolean keyPressed(KeyEvent e) {
        if (e.key() == 256) { onClose(); return true; }
        return super.keyPressed(e);
    }
    @Override public void onClose() { Minecraft.getInstance().setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void extractBackground(GuiGraphicsExtractor g, int x, int y, float delta) {}
    private static boolean inside(double x, double y, int left, int top, int w, int h) {
        return x >= left && x < left + w && y >= top && y < top + h;
    }
}
