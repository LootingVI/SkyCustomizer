package de.flori.skycustomizer.config;

import com.google.gson.Gson;
import de.flori.skycustomizer.features.ScoreboardCustomizer;
import de.flori.skycustomizer.util.FrameTimings;
import de.flori.skycustomizer.util.ProfileDiff;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import java.util.*;
import java.nio.file.Files;
import static de.flori.skycustomizer.render.ModernRenderHelper.*;
import static de.flori.skycustomizer.config.StudioWidgets.*;

public final class WorkbenchScreen extends StudioScreen {
    private final Screen parent;private final SkyCustomizerConfig config=SkyCustomizerConfig.get();
    private int page,x,y,w,h,scroll,profileIndex;
    private EditBox profileName;private String profileInput="",message="",profile="";
    private SkyCustomizerConfig candidate;private List<ProfileDiff.Change> differences=List.of();
    private FrameTimings.Stats closed,opened;private long refresh;
    private final SidebarPreview currentPreview=new SidebarPreview(),candidatePreview=new SidebarPreview();
    public WorkbenchScreen(Screen parent){super("Design Studio");this.parent=parent;}

    @Override
    protected void init(){
        w=Math.min(880,width-16);h=Math.min(540,height-16);x=(width-w)/2;y=(height-h)/2;
        clearWidgets();profileName=edit("New profile name",profileInput,48);profileName.setResponder(v->profileInput=v);addRenderableWidget(profileName);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float dt){
        backdrop(g,dt);fillRect(g,0,0,width,height,0xAD080604);fillRoundRect(g,x,y,w,h,10,C_PANEL);drawRoundBorder(g,x,y,w,h,10,C_BORDER);
        drawString(g,"Design Studio",x+16,y+13,C_TEXT);button(g,"Back",x+w-72,y+6,56,mx,my,false);
        String[] tabs={"Interface","Favorites","Profiles","Performance"};int tw=(w-28)/4;
        for(int i=0;i<tabs.length;i++)button(g,tabs[i],x+14+i*tw,y+38,tw-5,mx,my,page==i);
        profileName.visible=page==2;
        int top=y+75,bottom=y+h-37;g.enableScissor(x+10,top,x+w-10,bottom);
        switch(page){
            case 0 -> {
                String[] labels={"Smooth menu font","Interface animations","Background blur","Colors & Gradients"};
                String[] hints={"TrueType menu labels; Minecraft font in game previews.","Smooth scrolling. Disable for immediate transitions.","Optional world blur behind menus; off by default.","Favorite colors, recent swatches and reusable gradients."};
                boolean[] values={config.ui.smoothText,config.ui.animations,config.ui.backgroundBlur,false};
                for(int i=0;i<4;i++){int row=top+i*61-scroll;fillRoundRect(g,x+16,row,w-32,54,7,C_SURFACE);
                    drawString(g,labels[i],x+28,row+10,C_TEXT);drawString(g,font.plainSubstrByWidth(hints[i],w-60),x+28,row+32,C_TEXT_DIM);
                    button(g,i==3?"Open":values[i]?"On":"Off",x+w-86,row+7,57,mx,my,values[i]);}
                drawString(g,"Editor shortcuts: Ctrl+Z / Ctrl+Y, Shift+Ctrl+Z",x+22,top+257-scroll,C_GOLD_DARK);
            }
            case 1 -> {
                drawString(g,"Pin individual settings using the star in an editor.",x+18,top-scroll,C_TEXT_DIM);
                List<String> favorites=new ArrayList<>(config.ui.favorites);
                for(int i=0;i<favorites.size();i++){String id=favorites.get(i);int row=top+28+i*41-scroll;
                    fillRoundRect(g,x+16,row,w-32,35,6,C_SURFACE);icon(g,2,x+24,row+10,15,C_GOLD);
                    drawString(g,font.plainSubstrByWidth(id.replace('|',' '),w-95),x+46,row+13,C_TEXT);icon(g,9,x+w-43,row+10,15,C_TEXT_DIM);}
            }
            case 2 -> drawProfiles(g,mx,my,dt,top);
            case 3 -> drawPerformance(g,mx,my,top);
        }
        g.disableScissor();drawString(g,font.plainSubstrByWidth(message.isEmpty()?"Scroll for more options":message,w-32),x+16,y+h-24,C_TEXT_DIM);
    }

    private void drawProfiles(GuiGraphicsExtractor g,int mx,int my,float dt,int top){
        int base=top-scroll;
        profileName.visible=base>=top&&base+26<=y+h-37;
        if(profileName.visible)field(g,profileName,x+16,base,w-138,mx,my,dt);button(g,"Save new",x+w-110,base,94,mx,my,true);
        button(g,"<",x+16,base+36,30,mx,my,false);button(g,">",x+52,base+36,30,mx,my,false);
        drawString(g,font.plainSubstrByWidth(profile.isEmpty()?"Choose a saved profile":profile,w-246),x+94,base+44,C_TEXT);
        button(g,"Apply profile",x+w-128,base+36,112,mx,my,candidate!=null);
        if(candidate==null){drawString(g,"Select a profile to preview it without applying it.",x+20,base+83,C_TEXT_DIM);return;}
        int half=(w-48)/2, left=x+18,right=x+w/2+6;
        drawString(g,"CURRENT",left,base+83,C_GOLD_DARK);drawString(g,"SELECTED PROFILE",right,base+83,C_GOLD);
        previewBoard(g,config.scoreboard,left,base+103,half,110);previewBoard(g,candidate.scoreboard,right,base+103,half,110);
        drawString(g,differences.size()+" changed fields",left,base+226,C_TEXT_DIM);
        int row=base+247;
        for(var diff:differences){drawString(g,font.plainSubstrByWidth(diff.path(),w-40),left,row,C_GOLD_DARK);
            drawString(g,font.plainSubstrByWidth(diff.before(),half),left,row+13,C_TEXT_DIM);
            drawString(g,font.plainSubstrByWidth(diff.after(),half),right,row+13,C_TEXT);row+=34;}
    }

    private void previewBoard(GuiGraphicsExtractor g,SkyCustomizerConfig.ScoreboardConfig style,int left,int top,int width,int height){
        var snap=(style==config.scoreboard?currentPreview:candidatePreview).get(style);var size=ScoreboardCustomizer.measure(snap,style);
        float scale=Math.min(1,Math.min((float)width/Math.max(1,size.width()),(float)height/Math.max(1,size.height())));
        ScoreboardCustomizer.draw(g,snap,style,left,top,scale);
    }

    private void drawPerformance(GuiGraphicsExtractor g,int mx,int my,int top){
        if(System.nanoTime()-refresh>500_000_000L||closed==null){closed=FrameTimings.stats(false);opened=FrameTimings.stats(true);refresh=System.nanoTime();}
        int row=top-scroll;
        button(g,FrameTimings.recording()?"Stop recording":"Start recording",x+18,row,128,mx,my,FrameTimings.recording());
        button(g,"Reset",x+154,row,66,mx,my,false);
        String[] help={"1. Start recording, then close the config menu.","2. Keep the same scene and camera for at least 10 seconds.","3. Open the config again and wait another 10 seconds.","Frame interval includes frame caps and VSync; not GPU time."};
        for(int i=0;i<help.length;i++)drawString(g,font.plainSubstrByWidth(help[i],w-40),x+20,row+42+i*17,C_TEXT_DIM);
        stats(g,"MENU CLOSED",closed,x+20,row+121);stats(g,"MENU OPEN",opened,x+20,row+205);
        if(closed.count()<120||opened.count()<120)drawString(g,"Collect at least 120 frames in each mode for comparison.",x+20,row+288,C_GOLD_DARK);
        else drawString(g,String.format(Locale.ROOT,"Average difference: %+.2f ms per frame",opened.meanMs()-closed.meanMs()),x+20,row+288,C_GOLD);
        drawString(g,"Local measurement only. No telemetry is sent.",x+20,row+309,C_TEXT_DIM);
    }

    private void stats(GuiGraphicsExtractor g,String title,FrameTimings.Stats stats,int left,int top){
        drawString(g,title,left,top,C_GOLD);
        drawString(g,stats.count()==0?"No samples yet":String.format(Locale.ROOT,"%.1f FPS  |  %.2f ms average  |  %.2f ms p95",stats.fps(),stats.meanMs(),stats.p95Ms()),left,top+19,C_TEXT);
        drawString(g,stats.count()+" frames (latest 1200 maximum)",left,top+36,C_TEXT_DIM);
    }

    private void selectProfile(int direction){
        List<String> names=new ArrayList<>();
        for(String name:config.presets.savedPresets)try{if(Files.isRegularFile(config.getPresetPath(name)))names.add(name);}catch(IllegalArgumentException ignored){}
        if(names.isEmpty()){message="No saved profiles yet";candidate=null;profile="";return;}
        profileIndex=Math.floorMod(profileIndex+direction,names.size());profile=names.get(profileIndex);
        try{candidate=config.previewPreset(profile);Gson json=new Gson();differences=ProfileDiff.compare(json.toJsonTree(config),json.toJsonTree(candidate));message="Preview only - use Apply profile to change settings";}
        catch(Exception e){candidate=null;message="Could not read profile: "+e.getMessage();}
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e,boolean twice){
        if(e.button()!=0)return super.mouseClicked(e,twice);
        if(hit(e.x(),e.y(),x+w-72,y+6,56,24)){onClose();return true;}
        int tw=(w-28)/4;for(int i=0;i<4;i++)if(hit(e.x(),e.y(),x+14+i*tw,y+38,tw-5,24)){page=i;scroll=0;clearFocus();return true;}
        if(e.y()<y+75||e.y()>=y+h-37)return true;int top=y+75-scroll;
        if(page==0)for(int i=0;i<4;i++)if(hit(e.x(),e.y(),x+16,top+i*61,w-32,54)){
            if(i==0)config.ui.smoothText=!config.ui.smoothText;if(i==1)config.ui.animations=!config.ui.animations;
            if(i==2)config.ui.backgroundBlur=!config.ui.backgroundBlur;
            if(i==3){minecraft.setScreen(new PaletteScreen(this,"#E2944E",c->message="Selected "+c));return true;}
            config.save();return true;
        }

        if(page==1){List<String> favorites=new ArrayList<>(config.ui.favorites);for(int i=0;i<favorites.size();i++)if(hit(e.x(),e.y(),x+16,top+28+i*41,w-32,35)){
            String id=favorites.get(i);if(e.x()>=x+w-51){config.ui.favorites.remove(id);config.save();}
            else try{String[] parts=id.split("\\|",2);minecraft.setScreen(new FeatureEditorScreen(this,FeatureEditorScreen.Page.valueOf(parts[0]),parts[1]));}catch(Exception ignored){message="This setting is no longer available";}
            return true;}
        }

        if(page==2){
            if(hit(e.x(),e.y(),x+w-110,top,94,24)){
                try{if(Files.exists(config.getPresetPath(profileInput))){message="Name already exists; choose a new profile name";return true;}
                    config.savePreset(profileInput);message=Files.exists(config.getPresetPath(profileInput))?"Profile saved":"Profile could not be saved";}
                catch(Exception error){message=error.getMessage();}return true;
            }

            if(hit(e.x(),e.y(),x+16,top+36,30,24)){selectProfile(-1);return true;}
            if(hit(e.x(),e.y(),x+52,top+36,30,24)){selectProfile(1);return true;}
            if(hit(e.x(),e.y(),x+w-128,top+36,112,24)&&candidate!=null){message=config.loadPreset(profile)?"Profile applied":"Profile could not be applied";return true;}
        }

        if(page==3){if(hit(e.x(),e.y(),x+18,top,128,24)){if(FrameTimings.recording())FrameTimings.stop();else FrameTimings.start();return true;}
            if(hit(e.x(),e.y(),x+154,top,66,24)){FrameTimings.clear();refresh=0;return true;}}
        return super.mouseClicked(e,twice);
    }

    @Override
    public boolean mouseScrolled(double mx,double my,double horizontal,double vertical){
        int content=switch(page){case 0->290;case 1->30+config.ui.favorites.size()*41;case 2->candidate==null?120:247+differences.size()*34;default->340;};
        scroll=Math.clamp(scroll-(int)(vertical*27),0,Math.max(0,content-(h-112)));clearFocus();return true;
    }

    @Override
    public void onClose(){minecraft.setScreen(parent);}
}
