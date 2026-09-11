package de.flori.skycustomizer.config;

import de.flori.skycustomizer.render.UiFonts;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import static de.flori.skycustomizer.render.ModernRenderHelper.*;

final class StudioWidgets {
    static boolean hit(double x,double y,int l,int t,int w,int h){return x>=l&&x<l+w&&y>=t&&y<t+h;}
    static void button(GuiGraphicsExtractor g,String text,int x,int y,int w,int mx,int my,boolean active){
        fillRoundRect(g,x,y,w,24,6,active?C_ACTIVE:hit(mx,my,x,y,w,24)?C_HOVER:C_INPUT);
        drawRoundBorder(g,x,y,w,24,6,active?C_GOLD_DARK:C_BORDER);
        drawCenteredString(g,text,x+w/2,y+8,active?C_GOLD:C_TEXT);
    }
    static EditBox edit(String name,String value,int max){
        EditBox box=new EditBox(UiFonts.font(),0,0,100,18,Component.literal(name));
        box.setMaxLength(max);box.setValue(value==null?"":value);box.moveCursorToStart(false);box.setBordered(false);box.setTextShadow(false);box.setTextColor(C_TEXT);return box;
    }
    static void field(GuiGraphicsExtractor g,EditBox box,int x,int y,int w,int mx,int my,float dt){
        fillRoundRect(g,x,y,w,26,5,C_INPUT);box.setX(x+7);box.setY(y+9);box.setWidth(w-14);box.extractRenderState(g,mx,my,dt);
    }
}
