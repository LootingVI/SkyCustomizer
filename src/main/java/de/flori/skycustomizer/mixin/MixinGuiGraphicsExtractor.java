package de.flori.skycustomizer.mixin;

import de.flori.skycustomizer.features.TooltipCanvas;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(GuiGraphicsExtractor.class)
public class MixinGuiGraphicsExtractor {
    private boolean skycustomizer$scaledTooltip;

    @ModifyVariable(method = "tooltip", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int skycustomizer$positionTooltipX(int x) {
        if (!TooltipCanvas.enabled()) return x;
        var settings = de.flori.skycustomizer.config.SkyCustomizerConfig.get().items;
        int target = settings.tooltipCanvasPinned ? settings.tooltipCanvasPinnedX : x + settings.tooltipCanvasOffsetX;
        return Math.clamp(target, 0, ((GuiGraphicsExtractor) (Object) this).guiWidth());
    }

    @ModifyVariable(method = "tooltip", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private int skycustomizer$positionTooltipY(int y) {
        if (!TooltipCanvas.enabled()) return y;
        var settings = de.flori.skycustomizer.config.SkyCustomizerConfig.get().items;
        int target = settings.tooltipCanvasPinned ? settings.tooltipCanvasPinnedY : y + settings.tooltipCanvasOffsetY;
        return Math.clamp(target, 0, ((GuiGraphicsExtractor) (Object) this).guiHeight());
    }

    @Inject(method = "tooltip", at = @At("HEAD"))
    private void skycustomizer$scaleTooltip(Font font, List<ClientTooltipComponent> lines, int x, int y,
                                             ClientTooltipPositioner positioner, Identifier texture, CallbackInfo ci) {
        float scale = TooltipCanvas.scale();
        if (scale == 1.0f) return;
        GuiGraphicsExtractor graphics = (GuiGraphicsExtractor) (Object) this;
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(scale, scale);
        graphics.pose().translate(-x, -y);
        skycustomizer$scaledTooltip = true;
    }

    @Inject(method = "tooltip", at = @At("RETURN"))
    private void skycustomizer$finishTooltip(Font font, List<ClientTooltipComponent> lines, int x, int y,
                                              ClientTooltipPositioner positioner, Identifier texture, CallbackInfo ci) {
        if (!skycustomizer$scaledTooltip) return;
        ((GuiGraphicsExtractor) (Object) this).pose().popMatrix();
        skycustomizer$scaledTooltip = false;
    }
}
