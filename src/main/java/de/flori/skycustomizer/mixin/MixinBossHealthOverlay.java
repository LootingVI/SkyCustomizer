package de.flori.skycustomizer.mixin;

import de.flori.skycustomizer.features.visuals.VisualsCustomizer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.BossHealthOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BossHealthOverlay.class)
public class MixinBossHealthOverlay {

    @Inject(
            method = "extractRenderState",
            at = @At("HEAD"),
            cancellable = true
    )
    private void skycustomizer$hideBossbar(
            GuiGraphicsExtractor graphics,
            CallbackInfo ci
    ) {
        try {
            if (VisualsCustomizer.shouldHideBossbar()) {
                ci.cancel();
            }
        } catch (Exception ignored) {
        }
    }
}
