package de.flori.skycustomizer.config;

import com.google.gson.*;
import de.flori.skycustomizer.util.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import java.util.*;
import java.nio.file.*;
import javax.imageio.ImageIO;

public final class StudioChecks {
    private static int assertions;
    public static void run() throws Exception {
        UndoHistory<String> history=new UndoHistory<>();history.reset("initial");
        history.record("a",1_000_000_000L,true);history.record("ab",1_100_000_000L,true);
        check(history.undo().equals("initial"),"Typing coalesces into one undo step");
        check(history.redo().equals("ab"),"Redo restores coalesced typing");
        history.undo();history.record("branch",2_000_000_000L,false);
        check(!history.canRedo()&&history.undo().equals("initial"),"New edits discard only the redo branch");
        history.reset("0");for(int i=1;i<=150;i++)history.record(Integer.toString(i),i,false);
        for(int i=0;i<200;i++)history.undo();check(history.redo().equals("52"),"History bounded to 100 states");

        var self=new SkyCustomizerConfig.NametagRule();self.target="SELF";
        var levels=new SkyCustomizerConfig.NametagRule();levels.target="LEVEL";levels.minLevel=100;levels.maxLevel=200;levels.colorHex="#123456";
        var player=new SkyCustomizerConfig.NametagRule();player.target="PLAYER";player.username="Alex";
        check(NametagRules.match(List.of(self,levels),"Alex",true,120)==self,"First matching rule wins");
        check(NametagRules.match(List.of(self,levels),"Alex",false,120)==levels,"Level range matches other players");
        check(NametagRules.match(List.of(levels),"Alex",false,-1)==null,"Unknown levels do not match ranges");
        check(NametagRules.match(List.of(levels),"Alex",false,99)==null,"Range lower bound");
        check(NametagRules.match(List.of(player),"Alexander",false,120)==null,"Exact name, not substring");
        check(NametagRules.match(List.of(player),"alex",false,120)==player,"Case-insensitive player match");
        player.enabled=false;check(NametagRules.match(List.of(player),"Alex",false,120)==null,"Disabled rule ignored");
        check(NametagRules.level(Component.literal("[120] Alex"))==120,"Server level extraction");
        levels.prefix="[VIP] ";var tag=NametagRules.apply(Component.literal("[120] Alex"),"Alex",levels);
        check(tag.getString().equals("[120] [VIP] Alex"),"Rules preserve level prefix");

        String gradient=GradientText.create("AB","#000000","#FFFFFF");
        check(TextUtil.stripLegacyFormatting(gradient).equals("AB"),"Gradient text intact");
        check(gradient.equals("&#000000A&#FFFFFFB"),"Gradient endpoints exact");
        check(GradientText.create("","#000000","#FFFFFF").isEmpty(),"Empty gradient safe");
        check(TextUtil.parseLegacyFormatting(GradientText.create("A😀","#000000","#FFFFFF"),Style.EMPTY).getString().equals("A😀"),"Gradient preserves surrogate pairs");

        JsonElement before=JsonParser.parseString("{\"name\":{\"color\":\"red\",\"bold\":false}}");
        JsonElement after=JsonParser.parseString("{\"name\":{\"color\":\"gold\",\"bold\":false}}");
        var diff=ProfileDiff.compare(before,after);check(diff.size()==1&&diff.getFirst().path().equals("name.color"),"Profile diff isolates changed fields");
        check(before.getAsJsonObject().getAsJsonObject("name").get("color").getAsString().equals("red"),"Comparison is read-only");
        var migrated=SkyCustomizerConfig.decode("{\"hideScoreboardNumbers\":true,\"items\":{\"customGlintEnabled\":true},\"visuals\":{\"skyCustom\":true}}");
        check(migrated.scoreboard.hideScoreNumbers,"Legacy scoreboard number setting migrates into the editor setting");
        check(!new Gson().toJsonTree(migrated).getAsJsonObject().has("hideScoreboardNumbers"),"Removed settings are not written back");

        long now=1_000_000_000L;FrameTimings.start();
        for(int i=0;i<160;i++){FrameTimings.frame(now,false,true);now+=10_000_000L;}
        var closed=FrameTimings.stats(false);check(closed.count()>0&&Math.abs(closed.meanMs()-10)<.01,"Real closed frame intervals");
        for(int i=0;i<150;i++){FrameTimings.frame(now,true,true);now+=20_000_000L;}
        var opened=FrameTimings.stats(true);check(opened.count()>0&&Math.abs(opened.meanMs()-20)<.01,"Separate open-menu frame samples");
        check(Math.abs(opened.p95Ms()-20)<.01&&Math.abs(opened.fps()-50)<.01,"Frame percentile and FPS");
        FrameTimings.stop();int count=opened.count();FrameTimings.frame(now+20_000_000L,true,true);
        check(FrameTimings.stats(true).count()==count,"Stopped recorder takes no samples");
        FrameTimings.clear();check(FrameTimings.stats(false).count()==0,"Reset clears both sample groups");

        var saved=new SkyCustomizerConfig.ScoreboardConfig();saved.hiddenExactLines.add("Purse: 100");
        var draft=ScoreboardStyles.copy(saved);draft.hiddenExactLines.clear();
        check(saved.hiddenExactLines.size()==1,"Exact line hides are independently staged");
        Holder holder=new Holder();String snapshot=DraftSnapshots.capture(holder,"text","colors","enabled");
        holder.text[0]="changed";holder.colors.put(1,"blue");holder.enabled=false;
        DraftSnapshots.restore(holder,snapshot,"text","colors","enabled");
        check(holder.text[0].equals("original")&&holder.colors.isEmpty()&&holder.enabled,"Undo restores arrays, maps and primitive fields");

        Path assets=Path.of("src/main/resources/assets/skycustomizer");
        var font=java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT,assets.resolve("font/noto-sans.ttf").toFile());
        check(font.canDisplay('A')&&font.canDisplay('ä'),"Bundled TTF loads and includes Latin characters");
        check(Files.readString(assets.resolve("font/ofl.txt")).contains("SIL OPEN FONT LICENSE"),"Redistribution license bundled");
        var panel=ImageIO.read(assets.resolve("textures/gui/panel.png").toFile());
        check(panel.getWidth()==96&&panel.getHeight()==96,"Nine-slice source dimensions");
        int partial=0;for(int yy=0;yy<96;yy++)for(int xx=0;xx<96;xx++){int a=panel.getRGB(xx,yy)>>>24;if(a>0&&a<255)partial++;}
        check(partial>10,"Antialiased corners contain fractional coverage");
        for(int i=0;i<12;i++)check(ImageIO.read(assets.resolve("textures/gui/icon_"+i+".png").toFile())!=null,"Icon decodes: "+i);
        System.out.println("Design Studio: "+assertions+" checks passed.");
    }
    private static final class Holder{final String[] text={"original"};final Map<Integer,String> colors=new TreeMap<>();boolean enabled=true;}
    private static void check(boolean condition,String message){assertions++;if(!condition)throw new AssertionError(message);}
}
