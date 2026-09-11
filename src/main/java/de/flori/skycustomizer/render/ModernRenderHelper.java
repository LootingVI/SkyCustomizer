package de.flori.skycustomizer.render;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

public final class ModernRenderHelper {

    private ModernRenderHelper() {}

    public static final int C_PANEL      = 0xFF17110D;
    public static final int C_SURFACE    = 0xFF1D1610;
    public static final int C_HOVER      = 0xFF261B12;
    public static final int C_ACTIVE     = 0xFF352316;
    public static final int C_BORDER     = 0xFF382619;
    public static final int C_BORDER_ACT = 0xFF82502A;
    public static final int C_GOLD       = 0xFFE2944E;
    public static final int C_GOLD_DARK  = 0xFF9D5F30;
    public static final int C_GOLD_DIM   = 0x554E2B15;
    public static final int C_TEXT       = 0xFFF0E8DE;
    public static final int C_TEXT_DIM   = 0xFF978575;
    public static final int C_RED        = 0xFFD56E54;
    public static final int C_INPUT      = 0xFF15100C;
    public static final int C_INPUT_ACT  = 0xFF21160F;

    private static GuiGraphicsExtractor gx(Object g) {
        if (g instanceof GuiGraphicsExtractor ge) return ge;
        return null;
    }

    public static void fillRect(Object g, int x, int y, int w, int h, int col) {
        GuiGraphicsExtractor ge = gx(g);
        if (ge == null || w <= 0 || h <= 0) return;
        ge.fill(x, y, x + w, y + h, col);
    }

    public static void fillGradientV(Object g, int x, int y, int w, int h, int top, int bot) {
        GuiGraphicsExtractor ge = gx(g);
        if (ge == null || w <= 0 || h <= 0) return;
        ge.fillGradient(x, y, x + w, y + h, top, bot);
    }
    public static void fillGradientH(Object g,int x,int y,int w,int h,int left,int right){
        GuiGraphicsExtractor ge=gx(g);if(ge==null||w<=0||h<=0)return;
        ge.pose().pushMatrix();
        try{ge.pose().translate(x,y+h);ge.pose().rotate(-(float)Math.PI/2);ge.fillGradient(0,0,h,w,left,right);}
        finally{ge.pose().popMatrix();}
    }
    public static void saturationValue(Object g,int x,int y,int w,int h,float hue){
        fillRect(g,x,y,w,h,java.awt.Color.HSBtoRGB(hue,1,1));
        fillGradientH(g,x,y,w,h,0xFFFFFFFF,0x00FFFFFF);
        fillGradientV(g,x,y,w,h,0x00000000,0xFF000000);
    }
    public static void hueStrip(Object g,int x,int y,int w,int h){
        for(int i=0;i<6;i++){int left=x+i*w/6,right=x+(i+1)*w/6;
            fillGradientH(g,left,y,right-left,h,java.awt.Color.HSBtoRGB(i/6f,1,1),java.awt.Color.HSBtoRGB((i+1)/6f,1,1));}
    }

    private static final Identifier PANEL = Identifier.fromNamespaceAndPath("skycustomizer", "textures/gui/panel.png");
    private static final Identifier BORDER = Identifier.fromNamespaceAndPath("skycustomizer", "textures/gui/border.png");
    private static final Identifier[] ICONS = java.util.stream.IntStream.range(0,12)
            .mapToObj(i -> Identifier.fromNamespaceAndPath("skycustomizer", "textures/gui/icon_"+i+".png")).toArray(Identifier[]::new);

    public static void fillRoundRect(Object g, int x, int y, int w, int h, int r, int col) {
        if (w <= 0 || h <= 0) return;
        if (r < 2) { fillRect(g,x,y,w,h,col); return; }
        nineSlice(g, PANEL, x,y,w,h,r,col);
    }
    public static void drawRoundBorder(Object g, int x, int y, int w, int h, int r, int col) {
        if (r < 2) { drawBorder(g,x,y,w,h,col); return; }
        nineSlice(g,BORDER,x,y,w,h,r,col);
    }
    private static void nineSlice(Object g, Identifier texture, int x,int y,int w,int h,int radius,int color) {
        GuiGraphicsExtractor ge=gx(g); if(ge==null || w<=0 || h<=0) return;
        int r=Math.min(radius,Math.min(w,h)/2);
        int[] dx={x,x+r,x+w-r}, dy={y,y+r,y+h-r}, widths={r,w-2*r,r}, heights={r,h-2*r,r};
        for(int row=0;row<3;row++) for(int col=0;col<3;col++)
            if(widths[col]>0 && heights[row]>0)
                ge.blit(RenderPipelines.GUI_TEXTURED,texture,dx[col],dy[row],col*32f,row*32f,
                        widths[col],heights[row],32,32,96,96,color);
    }
    public static void icon(Object g,int id,int x,int y,int size,int color) {
        GuiGraphicsExtractor ge=gx(g); if(ge!=null && id>=0 && id<ICONS.length)
            ge.blit(RenderPipelines.GUI_TEXTURED,ICONS[id],x,y,0f,0f,size,size,96,96,96,96,color);
    }

    public static void drawBorder(Object g, int x, int y, int w, int h, int col) {
        fillRect(g, x,         y,         w, 1, col);
        fillRect(g, x,         y + h - 1, w, 1, col);
        fillRect(g, x,         y + 1,     1, h - 2, col);
        fillRect(g, x + w - 1, y + 1,     1, h - 2, col);
    }

    public static void drawShadow(Object g, int x, int y, int w, int h, int r) {
        drawRoundBorder(g, x - 1, y + 2, w + 2, h + 2, r + 1, 0x44000000);
        drawRoundBorder(g, x - 2, y + 4, w + 4, h + 4, r + 2, 0x28000000);
        drawRoundBorder(g, x - 3, y + 6, w + 6, h + 6, r + 3, 0x10000000);
    }

    public static void drawString(Object g, String text, int x, int y, int col) {
        GuiGraphicsExtractor ge = gx(g);
        if (ge == null || text == null || text.isEmpty()) return;
        Font font = UiFonts.font();
        ge.text(font, text, x, y, col, false);
    }

    public static void drawCenteredString(Object g, String text, int cx, int y, int col) {
        GuiGraphicsExtractor ge = gx(g);
        if (ge == null || text == null || text.isEmpty()) return;
        Font font = UiFonts.font();
        ge.centeredText(font, text, cx, y, col);
    }

    public static int getStringWidth(String text) {
        if (text == null) return 0;
        return UiFonts.font().width(text);
    }

}
