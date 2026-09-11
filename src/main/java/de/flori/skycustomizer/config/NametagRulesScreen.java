package de.flori.skycustomizer.config;

import com.google.gson.Gson;
import de.flori.skycustomizer.config.SkyCustomizerConfig.NametagRule;
import de.flori.skycustomizer.util.NametagRules;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.Consumer;
import static de.flori.skycustomizer.render.ModernRenderHelper.*;
import static de.flori.skycustomizer.config.StudioWidgets.*;

public final class NametagRulesScreen extends StudioScreen {
    private static final Gson JSON=new Gson();
    private final Screen parent;private final Consumer<List<NametagRule>> apply;
    private final List<NametagRule> rules=new ArrayList<>();
    private EditBox username,min,max,prefix,suffix;private int selected,x,y,w,h,scroll;private String error="";
    public NametagRulesScreen(Screen parent,List<NametagRule> source,Consumer<List<NametagRule>> apply){
        super("Nametag Rules");this.parent=parent;this.apply=apply;
        rules.addAll(Arrays.asList(JSON.fromJson(JSON.toJson(source),NametagRule[].class)));
    }
    @Override protected void init(){w=Math.min(640,width-16);h=Math.min(460,height-16);x=(width-w)/2;y=(height-h)/2;rebuild();startHistory();}
    private NametagRule selected(){return rules.isEmpty()?null:rules.get(Math.clamp(selected,0,rules.size()-1));}
    private void rebuild(){clearWidgets();NametagRule rule=selected();if(rule==null)return;
        username=edit("Exact username",rule.username,32);min=edit("Minimum level",Integer.toString(rule.minLevel),5);max=edit("Maximum level",Integer.toString(rule.maxLevel),5);
        prefix=edit("Prefix",rule.prefix,80);suffix=edit("Suffix",rule.suffix,80);
        username.setResponder(v->rule.username=v);prefix.setResponder(v->rule.prefix=v);suffix.setResponder(v->rule.suffix=v);
        min.setResponder(v->{try{rule.minLevel=Integer.parseInt(v);}catch(NumberFormatException ignored){}});
        max.setResponder(v->{try{rule.maxLevel=Integer.parseInt(v);}catch(NumberFormatException ignored){}});
        for(EditBox box:List.of(username,min,max,prefix,suffix))addRenderableWidget(box);
    }
    @Override protected String captureDraft(){return JSON.toJson(rules);}
    @Override protected void restoreDraft(String json){rules.clear();rules.addAll(Arrays.asList(JSON.fromJson(json,NametagRule[].class)));selected=Math.min(selected,Math.max(0,rules.size()-1));rebuild();}
    private boolean validSelected(){
        NametagRule rule=selected();if(rule==null)return true;
        if(rule.target.equals("LEVEL"))try{
            int lo=Integer.parseInt(min.getValue()),hi=Integer.parseInt(max.getValue());
            if(lo<0||hi<lo||hi>10000)throw new NumberFormatException();
            rule.minLevel=lo;rule.maxLevel=hi;
        }catch(NumberFormatException error){this.error="Level range must be 0-10000, minimum <= maximum";return false;}
        if(rule.target.equals("PLAYER")&&!username.getValue().matches("[A-Za-z0-9_]{1,16}")){error="Enter an exact Minecraft username";return false;}
        error="";return true;
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float dt){
        backdrop(g,dt);fillRect(g,0,0,width,height,0xAD080604);fillRoundRect(g,x,y,w,h,10,C_PANEL);
        drawString(g,"Nametag Rules",x+16,y+13,C_TEXT);drawString(g,"First matching rule wins. Overhead names only.",x+16,y+30,C_TEXT_DIM);
        String[] buttons={"<",">","+ Rule","Delete","Up","Down","Undo","Redo"};int bw=(w-28)/8;
        for(int i=0;i<buttons.length;i++)button(g,buttons[i],x+14+i*bw,y+47,bw-4,mx,my,false);
        NametagRule rule=selected();int row=y+88-scroll;
        g.enableScissor(x+10,y+79,x+w-10,y+h-38);
        if(rule==null){drawString(g,"Add a rule to begin",x+18,row,C_TEXT_DIM);}
        else{
            drawString(g,"RULE "+(selected+1)+" / "+rules.size(),x+18,row,C_GOLD);
            button(g,rule.enabled?"Enabled":"Disabled",x+w-100,row-5,83,mx,my,rule.enabled);
            button(g,"Target: "+rule.target,x+18,row+20,Math.min(153,w-36),mx,my,true);
            drawString(g,"Player / Min level / Max level",x+18,row+57,C_TEXT_DIM);
            username.visible=min.visible=max.visible=row+71>=y+79&&row+97<=y+h-38;
            if(username.visible){field(g,username,x+18,row+71,w-188,mx,my,dt);field(g,min,x+w-162,row+71,64,mx,my,dt);field(g,max,x+w-90,row+71,72,mx,my,dt);}
            drawString(g,"Prefix / Suffix",x+18,row+108,C_TEXT_DIM);
            prefix.visible=suffix.visible=row+123>=y+79&&row+149<=y+h-38;
            if(prefix.visible){field(g,prefix,x+18,row+123,(w-44)/2,mx,my,dt);field(g,suffix,x+w/2+4,row+123,(w-44)/2,mx,my,dt);}
            String[] controls={"Color",rule.bold?"Bold ON":"Bold",rule.italic?"Italic ON":"Italic",rule.hidden?"Hidden":"Visible"};
            int cw=(w-36)/4;for(int i=0;i<4;i++)button(g,controls[i],x+18+i*cw,row+160,cw-5,mx,my,false);
            if(row+219<y+h-44){drawString(g,"PREVIEW",x+18,row+197,C_GOLD_DARK);
                String name=rule.target.equals("PLAYER")&&!rule.username.isBlank()?rule.username:minecraft.getUser().getName();
                var preview=NametagRules.apply(Component.literal("[120] "+name),name,rule);
                g.text(minecraft.font,rule.hidden?Component.literal("Hidden nametag"):preview,x+18,row+213,-1,false);}
        }
        g.disableScissor();button(g,"Discard",x+w-160,y+h-32,68,mx,my,false);button(g,"Apply",x+w-84,y+h-32,68,mx,my,true);
        drawString(g,error.isEmpty()&&h<380?"Scroll for more options":error,x+16,y+h-24,error.isEmpty()?C_TEXT_DIM:C_RED);
    }
    @Override public boolean mouseClicked(MouseButtonEvent e,boolean twice){
        if(e.button()!=0)return super.mouseClicked(e,twice);checkpoint();
        if(hit(e.x(),e.y(),x+w-160,y+h-32,68,24)){onClose();return true;}
        if(hit(e.x(),e.y(),x+w-84,y+h-32,68,24)){
            if(!validSelected())return true;
            for(NametagRule rule:rules)if(rule.target.equals("PLAYER")&&!rule.username.matches("[A-Za-z0-9_]{1,16}")||rule.target.equals("LEVEL")&&(rule.minLevel<0||rule.maxLevel<rule.minLevel)){error="Check player names and level ranges";return true;}
            apply.accept(new ArrayList<>(Arrays.asList(JSON.fromJson(JSON.toJson(rules),NametagRule[].class))));onClose();return true;
        }
        int bw=(w-28)/8;for(int i=0;i<8;i++)if(hit(e.x(),e.y(),x+14+i*bw,y+47,bw-4,24)){
            if((i==0||i==1||i==2||i==4||i==5)&&!validSelected())return true;
            if(i==0&&!rules.isEmpty())selected=Math.floorMod(selected-1,rules.size());
            if(i==1&&!rules.isEmpty())selected=(selected+1)%rules.size();
            if(i==2){rules.add(new NametagRule());selected=rules.size()-1;}
            if(i==3&&!rules.isEmpty()){rules.remove(selected);selected=Math.max(0,selected-1);}
            if(i==4&&selected>0)Collections.swap(rules,selected,selected-- -1);
            if(i==5&&selected+1<rules.size())Collections.swap(rules,selected,selected++ +1);
            if(i==6||i==7){undo(i==7);return true;}rebuild();return true;
        }
        if(e.y()<y+79||e.y()>=y+h-38)return true;
        NametagRule rule=selected();if(rule==null)return true;int row=y+88-scroll;
        if(hit(e.x(),e.y(),x+w-100,row-5,83,24)){rule.enabled=!rule.enabled;return true;}
        if(hit(e.x(),e.y(),x+18,row+20,153,24)){List<String> targets=List.of("ANY","SELF","PLAYER","LEVEL");rule.target=targets.get((targets.indexOf(rule.target)+1)%4);return true;}
        int cw=(w-36)/4;for(int i=0;i<4;i++)if(hit(e.x(),e.y(),x+18+i*cw,row+160,cw-5,24)){
            if(i==0)minecraft.setScreen(new ColorPickerScreen(this,rule.colorHex,c->rule.colorHex=c));
            if(i==1)rule.bold=!rule.bold;if(i==2)rule.italic=!rule.italic;if(i==3)rule.hidden=!rule.hidden;return true;
        }
        return super.mouseClicked(e,twice);
    }
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public boolean mouseScrolled(double mx,double my,double horizontal,double vertical){scroll=Math.clamp(scroll-(int)(vertical*25),0,Math.max(0,340-h+40));clearFocus();return true;}
}
