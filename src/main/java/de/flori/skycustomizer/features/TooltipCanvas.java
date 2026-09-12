package de.flori.skycustomizer.features;

import de.flori.skycustomizer.config.SkyCustomizerConfig;
import de.flori.skycustomizer.config.TooltipCanvasScreen;
import net.minecraft.client.Minecraft;

public final class TooltipCanvas {
    private TooltipCanvas() {}

    public static boolean enabled() {
        var minecraft = Minecraft.getInstance();
        return SkyCustomizerConfig.get().enabled && SkyCustomizerConfig.get().items.tooltipCanvasEnabled
                && !(minecraft.screen instanceof TooltipCanvasScreen);
    }

    public static float scale() {
        if (!enabled()) return 1.0f;
        return Math.clamp(SkyCustomizerConfig.get().items.tooltipCanvasScale, 0.5f, 2.0f);
    }
}
