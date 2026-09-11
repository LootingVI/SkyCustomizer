package de.flori.skycustomizer.config;

import com.google.gson.Gson;
import de.flori.skycustomizer.util.TextUtil;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Style;
import java.util.*;
import java.util.function.Consumer;
import static de.flori.skycustomizer.render.ModernRenderHelper.*;
import static de.flori.skycustomizer.config.StudioWidgets.*;

public final class LoreEditorScreen extends StudioScreen {
    private final Screen parent;private final Consumer<String> apply;private final String item;
    private final List<String> lines=new ArrayList<>();private final List<EditBox> boxes=new ArrayList<>();
    private int selected,scroll,drag=-1,x,y,w,h,listW;private String error="";
    public LoreEditorScreen(Screen parent,String lore,String item,Consumer<String> apply){
        super("Lore Editor");this.parent=parent;this.apply=apply;this.item=item;
        if(!lore.isEmpty())lines.addAll(Arrays.asList(lore.replace("\\n","\n").split("\n",-1)));
    }
    @Override protected void init(){layout();rebuild();startHistory();}
    private void layout(){w=Math.min(820,width-16);h=Math.min(510,height-16);x=(width-w)/2;y=(height-h)/2;listW=w>=570?w*3/5:w;}
    private void rebuild(){clearWidgets();boxes.clear();for(int i=0;i<lines.size();i++){
        final int n=i;EditBox box=edit("Lore line "+(i+1),lines.get(i),1024);box.setResponder(text->{lines.set(n,text);error="";});boxes.add(box);addRenderableWidget(box);
    }}
    @Override protected String captureDraft(){return new Gson().toJson(lines);}
    @Override protected void restoreDraft(String state){lines.clear();lines.addAll(Arrays.asList(new Gson().fromJson(state,String[].class)));selected=Math.min(selected,Math.max(0,lines.size()-1));rebuild();}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float dt){
        layout();backdrop(g,dt);fillRect(g,0,0,width,height,0xAD080604);fillRoundRect(g,x,y,w,h,10,C_PANEL);
        drawString(g,"Lore Editor",x+16,y+14,C_TEXT);drawString(g,"Drag the handle to reorder lines",x+16,y+31,C_TEXT_DIM);
        String[] actions={"+ Line","Bold","Italic","Reset","Color","Undo","Redo"};
        int bw=Math.min(63,(w-32)/7);for(int i=0;i<actions.length;i++)button(g,actions[i],x+16+i*bw,y+48,bw-4,mx,my,false);
        int top=y+82,bottom=y+h-44;g.enableScissor(x+8,top,x+listW-8,bottom);
        for(int i=0;i<boxes.size();i++){
            int rowY=top+i*38-scroll;EditBox box=boxes.get(i);box.visible=rowY+34>top&&rowY<bottom;
            if(!box.visible)continue;
            if(box.isFocused())selected=i;
            fillRoundRect(g,x+12,rowY,listW-24,34,6,i==selected?C_ACTIVE:C_SURFACE);
            icon(g,3,x+18,rowY+9,15,C_GOLD_DARK);
            field(g,box,x+40,rowY+4,listW-85,mx,my,dt);icon(g,9,x+listW-36,rowY+9,15,C_TEXT_DIM);
        }g.disableScissor();
        if(listW<w){
            int px=x+listW+14;drawString(g,"TOOLTIP PREVIEW",px,y+90,C_GOLD);
            g.enableScissor(px,y+111,x+w-12,bottom);
            g.text(minecraft.font,TextUtil.parseLegacyFormatting(item.isBlank()?"Item preview":item,Style.EMPTY.withColor(0xFFFFFF)),px,y+116,-1,false);
            int lineY=y+134;for(String line:lines)for(var part:minecraft.font.split(TextUtil.parseLegacyFormatting(line,Style.EMPTY.withColor(0xAAAAAA)),w-listW-30)){
                g.text(minecraft.font,part,px,lineY,-1,false);lineY+=12;
            }g.disableScissor();
        }
        button(g,"Discard",x+w-158,y+h-33,68,mx,my,false);button(g,"Apply",x+w-82,y+h-33,68,mx,my,true);
        drawString(g,error.isEmpty()?lines.size()+" lines / "+String.join("\\n",lines).length()+" of 1024 characters":error,x+16,y+h-24,error.isEmpty()?C_TEXT_DIM:C_RED);
    }
    @Override public boolean mouseClicked(MouseButtonEvent e,boolean twice){
        if(e.button()!=0)return super.mouseClicked(e,twice);
        if(hit(e.x(),e.y(),x+w-158,y+h-33,68,24)){onClose();return true;}
        if(hit(e.x(),e.y(),x+w-82,y+h-33,68,24)){
            String lore=String.join("\\n",lines);if(lore.length()>1024){error="Lore exceeds 1024 characters";return true;}apply.accept(lore);onClose();return true;
        }
        int bw=Math.min(63,(w-32)/7);
        for(int i=0;i<7;i++)if(hit(e.x(),e.y(),x+16+i*bw,y+48,bw-4,24)){
            checkpoint();if(i==0){if(lines.size()<64){lines.add("");selected=lines.size()-1;rebuild();scroll=Math.max(0,lines.size()*38-(h-126));}}
            else if(i==5||i==6)undo(i==6);
            else if(!boxes.isEmpty()){
                EditBox box=boxes.get(Math.clamp(selected,0,boxes.size()-1));
                if(i==4)minecraft.setScreen(new ColorPickerScreen(this,"#FFAA00",color->{box.insertText("&"+color);checkpoint();}));
                else box.insertText(i==1?"&l":i==2?"&o":"&r");
            }return true;
        }
        if(e.y()>=y+82&&e.y()<y+h-44)for(int i=0;i<lines.size();i++){
            int rowY=y+82+i*38-scroll;
            if(hit(e.x(),e.y(),x+14,rowY,23,34)){checkpoint();selected=i;drag=i;return true;}
            if(hit(e.x(),e.y(),x+listW-39,rowY,24,34)){checkpoint();lines.remove(i);selected=Math.max(0,Math.min(selected,lines.size()-1));rebuild();return true;}
        }
        return super.mouseClicked(e,twice);
    }
    @Override public boolean mouseDragged(MouseButtonEvent e,double dx,double dy){
        if(drag>=0&&!lines.isEmpty()){
            int target=Math.clamp((int)(e.y()-y-82+scroll)/38,0,lines.size()-1);
            if(target!=drag){String line=lines.remove(drag);lines.add(target,line);drag=target;selected=target;rebuild();}return true;
        }return super.mouseDragged(e,dx,dy);
    }
    @Override public boolean mouseReleased(MouseButtonEvent e){if(drag>=0){drag=-1;checkpoint();return true;}return super.mouseReleased(e);}
    @Override public boolean mouseScrolled(double mx,double my,double horizontal,double vertical){scroll=Math.clamp(scroll-(int)(vertical*32),0,Math.max(0,lines.size()*38-(h-126)));clearFocus();return true;}
    @Override public boolean keyPressed(KeyEvent e){if(e.key()==256){onClose();return true;}return super.keyPressed(e);}
    @Override public void onClose(){minecraft.setScreen(parent);}
}
