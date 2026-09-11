package de.flori.skycustomizer.config;

import de.flori.skycustomizer.util.ColorUtil;
import de.flori.skycustomizer.util.GradientText;
import de.flori.skycustomizer.util.TextUtil;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Style;
import java.util.*;
import java.util.function.Consumer;
import static de.flori.skycustomizer.render.ModernRenderHelper.*;
import static de.flori.skycustomizer.config.StudioWidgets.*;

public final class PaletteScreen extends StudioScreen {
    private final Screen parent;private final Consumer<String> apply;
    private String selected,start="#E2944E",end="#FF55FF",message="",textValue="My custom gradient",nameValue="Warm glow";
    private EditBox text,name;private int x,y,w,h,scroll,preset;
    public PaletteScreen(Screen parent,String color,Consumer<String> apply){super("Colors & Gradients");this.parent=parent;this.apply=apply;selected=color;}
    @Override protected void init(){w=Math.min(670,width-16);h=Math.min(450,height-16);x=(width-w)/2;y=(height-h)/2;clearWidgets();
        text=edit("Gradient text",textValue,128);name=edit("Palette name",nameValue,32);
        text.setResponder(v->textValue=v);name.setResponder(v->nameValue=v);addRenderableWidget(text);addRenderableWidget(name);startHistory();
    }
    @Override protected String[] historyFields(){return new String[]{"start","end","selected","textValue","nameValue"};}
    @Override protected void afterHistory(){text.setValue(textValue);name.setValue(nameValue);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float dt){
        backdrop(g,dt);fillRect(g,0,0,width,height,0xAD080604);fillRoundRect(g,x,y,w,h,10,C_PANEL);
        drawString(g,"Colors & Gradients",x+16,y+14,C_TEXT);button(g,"Back",x+w-75,y+7,59,mx,my,false);
        int cy=y+48-scroll;g.enableScissor(x+10,y+42,x+w-10,y+h-38);
        drawString(g,"FAVORITES",x+18,cy,C_GOLD);swatches(g,SkyCustomizerConfig.get().ui.favoriteColors,cy+17);
        drawString(g,"RECENT COLORS",x+18,cy+81,C_GOLD_DARK);swatches(g,SkyCustomizerConfig.get().ui.recentColors,cy+98);
        fillRoundRect(g,x+18,cy+134,23,24,5,ColorUtil.fromHex(selected));
        button(g,"Favorite",x+48,cy+134,73,mx,my,false);button(g,"Copy hex",x+127,cy+134,73,mx,my,false);button(g,"Use color",x+206,cy+134,77,mx,my,true);
        drawString(g,"TEXT GRADIENT",x+18,cy+176,C_GOLD);
        button(g,"Start "+start,x+18,cy+193,126,mx,my,false);button(g,"End "+end,x+153,cy+193,126,mx,my,false);
        text.visible=cy+228>=y+42&&cy+254<=y+h-38;name.visible=cy+316>=y+42&&cy+342<=y+h-38;
        if(text.visible)field(g,text,x+18,cy+228,w-36,mx,my,dt);
        g.text(minecraft.font,TextUtil.parseLegacyFormatting(GradientText.create(textValue,start,end),Style.EMPTY),x+18,cy+265,-1,false);
        button(g,"Copy formatted text",x+18,cy+283,142,mx,my,false);
        button(g,"Load saved",x+168,cy+283,111,mx,my,false);
        if(name.visible)field(g,name,x+18,cy+316,w-145,mx,my,dt);
        button(g,"Save gradient",x+w-118,cy+316,100,mx,my,true);
        g.disableScissor();drawString(g,message.isEmpty()?"Scroll for gradient text and saved palettes":message,x+16,y+h-24,C_TEXT_DIM);
    }
    private void swatches(GuiGraphicsExtractor g,List<String> colors,int sy){int columns=Math.max(1,(w-36)/27);
        for(int i=0;i<colors.size();i++){String hex=colors.get(i);int sx=x+18+(i%columns)*27,yy=sy+(i/columns)*27;
            fillRoundRect(g,sx,yy,22,22,5,ColorUtil.fromHex(hex));if(hex.equals(selected))drawRoundBorder(g,sx-1,yy-1,24,24,6,C_TEXT);}
    }
    @Override public boolean mouseClicked(MouseButtonEvent e,boolean twice){
        if(e.button()!=0)return super.mouseClicked(e,twice);checkpoint();
        if(hit(e.x(),e.y(),x+w-75,y+7,59,24)){onClose();return true;}
        if(e.y()<y+42||e.y()>=y+h-38)return true;int cy=y+48-scroll;
        int columns=Math.max(1,(w-36)/27);List<List<String>> palettes=List.of(SkyCustomizerConfig.get().ui.favoriteColors,SkyCustomizerConfig.get().ui.recentColors);
        for(int group=0;group<2;group++){List<String> colors=palettes.get(group);for(int i=0;i<colors.size();i++)
            if(hit(e.x(),e.y(),x+18+i%columns*27,cy+(group==0?17:98)+i/columns*27,22,22)){selected=colors.get(i);return true;}}
        if(hit(e.x(),e.y(),x+48,cy+134,73,24)){PaletteStore.favorite(selected);return true;}
        if(hit(e.x(),e.y(),x+127,cy+134,73,24)){minecraft.keyboardHandler.setClipboard(selected);message="Hex copied";return true;}
        if(hit(e.x(),e.y(),x+206,cy+134,77,24)){apply.accept(selected);onClose();return true;}
        if(hit(e.x(),e.y(),x+18,cy+193,126,24)){minecraft.setScreen(new ColorPickerScreen(this,start,c->start=c));return true;}
        if(hit(e.x(),e.y(),x+153,cy+193,126,24)){minecraft.setScreen(new ColorPickerScreen(this,end,c->end=c));return true;}
        if(hit(e.x(),e.y(),x+18,cy+283,142,24)){minecraft.keyboardHandler.setClipboard(GradientText.create(textValue,start,end));message="Formatted gradient copied";return true;}
        if(hit(e.x(),e.y(),x+168,cy+283,111,24)){
            var entries=new ArrayList<>(SkyCustomizerConfig.get().ui.gradients.entrySet());if(!entries.isEmpty()){
                var entry=entries.get(Math.floorMod(preset++,entries.size()));String[] stops=entry.getValue().split(",");
                if(stops.length==2&&FeatureEditorScreen.normalizeColor(stops[0])!=null&&FeatureEditorScreen.normalizeColor(stops[1])!=null){start=stops[0];end=stops[1];name.setValue(entry.getKey());message="Loaded "+entry.getKey();}}
            else message="No saved gradients yet";return true;
        }
        if(hit(e.x(),e.y(),x+w-118,cy+316,100,24)){
            if(nameValue.isBlank()){message="Enter a gradient name";return true;}
            var config=SkyCustomizerConfig.get();config.ui.gradients.put(nameValue,start+","+end);config.save();message="Gradient saved";return true;
        }
        return super.mouseClicked(e,twice);
    }
    @Override public boolean mouseScrolled(double mx,double my,double horizontal,double vertical){scroll=Math.clamp(scroll-(int)(vertical*25),0,Math.max(0,400-h+45));clearFocus();return true;}
    @Override public void onClose(){minecraft.setScreen(parent);}
}
