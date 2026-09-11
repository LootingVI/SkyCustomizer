package de.flori.skycustomizer.mixin;

import de.flori.skycustomizer.features.ScoreboardCustomizer;
import de.flori.skycustomizer.config.HudEditorScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.scores.Objective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class MixinScoreboardSidebar {

    @Inject(method = "displayScoreboardSidebar", at = @At("HEAD"), cancellable = true)
    private void skycustomizer$renderCompactSidebar(GuiGraphicsExtractor graphics, Objective objective, CallbackInfo ci) {
        try {

            if (Minecraft.getInstance().screen instanceof HudEditorScreen
                    || ScoreboardCustomizer.isEnabled()
                    && ScoreboardCustomizer.renderCompactSidebar(graphics, objective)) {
                ci.cancel();
            }
        } catch (Exception ignored) {

        }
    }
}
