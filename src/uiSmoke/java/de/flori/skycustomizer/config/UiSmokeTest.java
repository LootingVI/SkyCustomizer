package de.flori.skycustomizer.config;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Screenshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import java.nio.file.*;
import java.util.*;
import java.util.function.Supplier;

public final class UiSmokeTest implements net.fabricmc.api.ClientModInitializer {
    private static int ticks,stage;private static boolean initialized;
    private static volatile boolean capturing;
    private static List<Supplier<Screen>> screens;
    private static boolean failed;
    @Override public void onInitializeClient() {
        if (net.fabricmc.loader.api.FabricLoader.getInstance().isDevelopmentEnvironment()
                && Boolean.getBoolean("skycustomizer.uiSmoke")) ClientTickEvents.END_CLIENT_TICK.register(UiSmokeTest::tick);
    }
    private static void tick(Minecraft mc){
        if(mc.getOverlay()!=null)return;
        if(!initialized){
            if(++ticks<60)return;initialized=true;ticks=0;
            mc.options.guiScale().set(2);mc.resizeGui();
            var c=SkyCustomizerConfig.get();
            try { NamePresentationSmoke.run(mc); }
            catch (Throwable error) { failed = true; error.printStackTrace(); }
            try { DiscordPlaceholderSmoke.runtime(mc); }
            catch (Throwable error) { failed = true; error.printStackTrace(); }
            ScoreboardStudioSmoke.prepare();
            c.items.heldItemLabels.put("uuid:preview","Hyperion");c.items.heldItemNames.put("uuid:preview","&#E2944E&lEmber Blade");
            c.items.heldItemLore.put("uuid:preview","&7Forged for exploration\\n&#55FFFFA little more personal.");
            c.items.tooltipCanvasEnabled=true;
            c.visuals.performanceHud=true;
            c.visuals.actionbarComposerEnabled=true;
            screens=List.of(()->new SkyCustomizerConfigScreen(null),()->new FeatureEditorScreen(null,FeatureEditorScreen.Page.ITEMS),
                    ()->new FeatureEditorScreen(null,FeatureEditorScreen.Page.NAME),
                    ()->new LoreEditorScreen(null,c.items.heldItemLore.get("uuid:preview"),"&6Ember Blade",v->{}),
                    ()->new TooltipCanvasScreen(null,c.items,v->{}),
                    ()->new NametagRulesScreen(null,List.of(new SkyCustomizerConfig.NametagRule()),v->{}),
                    ()->new FeatureEditorScreen(null,FeatureEditorScreen.Page.SCOREBOARD),()->new ColorPickerScreen(null,"#E2944E",v->{}),
                    ()->new PaletteScreen(null,"#E2944E",v->{}),()->new WorkbenchScreen(null),
                    ()->workbench(1),()->workbench(2),()->workbench(3),
                    ()->new LevelColorEditorScreen(null),()->ScoreboardStudioSmoke.screen(mc,false,0),
                    ()->hudEditor(),
                    ()->compact(mc,()->new NametagRulesScreen(null,List.of(new SkyCustomizerConfig.NametagRule()),v->{})),
                    ()->compact(mc,()->new PaletteScreen(null,"#E2944E",v->{})),
                    ()->compact(mc,()->new FeatureEditorScreen(null,FeatureEditorScreen.Page.ITEMS)),
                    ()->ScoreboardStudioSmoke.screen(mc,false,2),()->ScoreboardStudioSmoke.screen(mc,false,1),
                    ()->ScoreboardStudioSmoke.screen(mc,true,0),()->ScoreboardStudioSmoke.screen(mc,true,2),
                    ()->ScoreboardStudioSmoke.screen(mc,true,0),
                    ()->new FeatureEditorScreen(null,FeatureEditorScreen.Page.DISCORD),
                    ()->{mc.options.guiScale().set(2);mc.resizeGui();return new FeatureEditorScreen(null,FeatureEditorScreen.Page.DISCORD);},
                    ()->new FeatureEditorScreen(null,FeatureEditorScreen.Page.DISCORD,"state"),
                    ()->new DiscordPlaceholderScreen(null,c.discord,v->{}),
                    ()->compact(mc,()->new DiscordPlaceholderScreen(null,c.discord,v->{})));
            c.ui.favorites.add("NAME|Custom name");
            c.savePreset("smoke-reference");
            c.scoreboard.titleColorHex="#E2944E";
            mc.setScreen(screens.getFirst().get());return;
        }
        if(capturing)return;
        if(stage==2&&ticks==10)checkFeatureHistory(mc);
        if(stage==14&&ticks==5)ScoreboardStudioSmoke.drop((ScoreboardEditorScreen)mc.screen);
        if(stage==14&&ticks==20)try { ScoreboardStudioSmoke.check((ScoreboardEditorScreen)mc.screen); }
        catch(Throwable error) { failed=true; error.printStackTrace(); }
        if(stage==22&&ticks==5)try { ScoreboardStudioSmoke.inlineExample((ScoreboardEditorScreen)mc.screen); }
        catch(Throwable error) { failed=true; error.printStackTrace(); }
        if(stage==26&&ticks==10)try { DiscordPlaceholderSmoke.insertion(mc); }
        catch(Throwable error) { failed=true; error.printStackTrace(); }
        if(stage==27&&ticks==5)DiscordPlaceholderSmoke.search(mc.screen,"item");
        if(stage==28&&ticks==5)DiscordPlaceholderSmoke.search(mc.screen,"position");
        if(++ticks<35)return;
        capturing=true;int current=stage;
        Screenshot.takeScreenshot(mc.getMainRenderTarget(),image->{
            try{Path dir=Path.of("screenshots","verified");Files.createDirectories(dir);image.writeToFile(dir.resolve(String.format(Locale.ROOT,"studio-%02d.png",current)));}
            catch(Exception e){e.printStackTrace();}finally{image.close();}
            mc.execute(()->{
                stage++;ticks=0;capturing=false;
                if(stage>=screens.size()){System.out.println(failed?"[SkyCustomizer] UI_SMOKE_FAILED":"[SkyCustomizer] UI_SMOKE_COMPLETE");mc.stop();}
                else mc.setScreen(screens.get(stage).get());
            });
        });
    }
    private static Screen compact(Minecraft mc,Supplier<Screen> screen){mc.options.guiScale().set(4);mc.resizeGui();return screen.get();}
    private static Screen hudEditor(){
        HudEditorScreen screen=new HudEditorScreen(null);
        try{
            var field=HudEditorScreen.class.getDeclaredField("selected");field.setAccessible(true);
            Object performance=Arrays.stream(field.getType().getEnumConstants()).filter(value->value.toString().equals("PERFORMANCE")).findFirst().orElseThrow();
            field.set(screen,performance);
        }catch(Exception error){throw new IllegalStateException(error);}
        return screen;
    }
    private static Screen workbench(int page){
        WorkbenchScreen screen=new WorkbenchScreen(null);
        try{var field=WorkbenchScreen.class.getDeclaredField("page");field.setAccessible(true);field.setInt(screen,page);
            if(page==2){var method=WorkbenchScreen.class.getDeclaredMethod("selectProfile",int.class);method.setAccessible(true);method.invoke(screen,0);
                var candidate=WorkbenchScreen.class.getDeclaredField("candidate");candidate.setAccessible(true);
                if(candidate.get(screen)==null)throw new AssertionError("Profile preview did not load");
                System.out.println("[SkyCustomizer] UI_PROFILE_PREVIEW_PASSED");}}
        catch(Exception error){throw new IllegalStateException(error);}return screen;
    }
    private static void checkFeatureHistory(Minecraft mc){
        try{
            FeatureEditorScreen screen=(FeatureEditorScreen)mc.screen;
            String original=screen.captureDraft();
            var field=screen.children().stream().filter(child->child instanceof net.minecraft.client.gui.components.EditBox box&&box.getMessage().getString().equalsIgnoreCase("Custom name"))
                    .map(child->(net.minecraft.client.gui.components.EditBox)child).findFirst().orElseThrow();
            field.setValue("Undo preview");screen.checkpoint();screen.undo(false);
            if(!screen.captureDraft().equals(original))throw new AssertionError("Feature editor undo mismatch");
            screen.undo(true);
            if(!screen.captureDraft().contains("Undo preview"))throw new AssertionError("Feature editor redo mismatch");
            screen.undo(false);System.out.println("[SkyCustomizer] UI_HISTORY_PASSED");
        }catch(Throwable error){failed=true;error.printStackTrace();}
    }
}
