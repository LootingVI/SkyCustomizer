package de.flori.skycustomizer.features.visuals;

import de.flori.skycustomizer.config.SkyCustomizerConfig;
import de.flori.skycustomizer.util.ColorUtil;
import net.minecraft.network.chat.Component;

public final class VisualsCustomizer {
    private VisualsCustomizer() {}

    private static boolean enabled(SkyCustomizerConfig.VisualsConfig visuals) {
        return SkyCustomizerConfig.get().enabled && visuals.enabled;
    }

    public static boolean shouldHideTitles() {
        var c = SkyCustomizerConfig.get().visuals;
        return enabled(c) && c.customTitles && c.hideTitles;
    }

    public static Component customizeTitle(Component original) {
        var c = SkyCustomizerConfig.get().visuals;
        if (!enabled(c) || !c.customTitles || c.hideTitles) return original;
        if (original == null) return null;
        String text = original.getString();
        if (text.isEmpty()) return original;
        if (c.titleChroma) {

            var out = Component.empty();
            for (int i = 0; i < text.length(); i++) {
                int rgb = ColorUtil.chromaChar(i, 1.0f) & 0xFFFFFF;
                out.append(Component.literal(String.valueOf(text.charAt(i))).withStyle(s -> s.withColor(net.minecraft.network.chat.TextColor.fromRgb(rgb)).withBold(true)));
            }
            return out;
        }
        int rgb = ColorUtil.fromHex(c.titleHex) & 0xFFFFFF;
        return Component.literal(text).withStyle(s -> s.withColor(net.minecraft.network.chat.TextColor.fromRgb(rgb)).withBold(true));
    }

    public static Component customizeSubtitle(Component original) {
        var c = SkyCustomizerConfig.get().visuals;
        if (!enabled(c) || !c.customTitles) return original;
        if (original == null) return null;
        int rgb = ColorUtil.fromHex(c.subtitleHex) & 0xFFFFFF;
        String text = original.getString();
        return Component.literal(text).withStyle(s -> s.withColor(net.minecraft.network.chat.TextColor.fromRgb(rgb)));
    }

    public static boolean shouldHideBossbar() {
        var c = SkyCustomizerConfig.get().visuals;
        return enabled(c) && c.bossbarCustom && c.bossbarHide;
    }

    public static boolean shouldHideActionbar() {
        var c = SkyCustomizerConfig.get().visuals;
        return enabled(c) && c.actionbarCustom && (c.actionbarHide || c.actionbarComposerEnabled);
    }

    public static boolean shouldHideVignette() {
        var c = SkyCustomizerConfig.get().visuals;
        return enabled(c) && c.vignetteHide;
    }
}
