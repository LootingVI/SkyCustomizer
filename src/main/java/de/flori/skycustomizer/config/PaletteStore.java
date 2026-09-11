package de.flori.skycustomizer.config;

public final class PaletteStore {
    private PaletteStore() {}
    public static void remember(String color){
        String hex=FeatureEditorScreen.normalizeColor(color);if(hex==null)return;
        var config=SkyCustomizerConfig.get();var recent=config.ui.recentColors;
        recent.remove(hex);recent.addFirst(hex);while(recent.size()>12)recent.removeLast();config.save();
    }
    public static void favorite(String color){
        String hex=FeatureEditorScreen.normalizeColor(color);if(hex==null)return;
        var config=SkyCustomizerConfig.get();var colors=config.ui.favoriteColors;
        if(!colors.remove(hex)){colors.add(hex);if(colors.size()>24)colors.removeFirst();}config.save();
    }
}
