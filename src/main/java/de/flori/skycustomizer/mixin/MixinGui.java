package de.flori.skycustomizer.mixin;

import de.flori.skycustomizer.features.visuals.VisualsCustomizer;
import de.flori.skycustomizer.features.visuals.HudCustomizer;
import de.flori.skycustomizer.features.visuals.PerformanceHud;
import de.flori.skycustomizer.config.HudEditorScreen;
import de.flori.skycustomizer.config.SkyCustomizerConfig;
import de.flori.skycustomizer.util.HudLayout;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Gui;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class MixinGui {

    @Inject(method = "setTitle", at = @At("HEAD"), cancellable = true)
    private void skycustomizer$onTitle(Component title, CallbackInfo ci) {
        try {
            if (VisualsCustomizer.shouldHideTitles()) {
                ci.cancel();
            }
        } catch (Exception ignored) {}
    }

    @Inject(method = "extractVignette", at = @At("HEAD"), cancellable = true)
    private void skycustomizer$hideVignette(GuiGraphicsExtractor graphics, net.minecraft.world.entity.Entity camera, CallbackInfo ci) {
        PerformanceHud.draw(graphics);
        if (VisualsCustomizer.shouldHideVignette()) ci.cancel();
    }

    @ModifyVariable(method = "setTitle", at = @At("HEAD"), argsOnly = true)
    private Component skycustomizer$modifyTitle(Component original) {
        try {
            return VisualsCustomizer.customizeTitle(original);
        } catch (Exception e) {
            return original;
        }
    }

    @ModifyVariable(method = "setSubtitle", at = @At("HEAD"), argsOnly = true)
    private Component skycustomizer$modifySubtitle(Component original) {
        try {
            return VisualsCustomizer.customizeSubtitle(original);
        } catch (Exception e) {
            return original;
        }
    }

    @Inject(method = "setOverlayMessage", at = @At("HEAD"), cancellable = true)
    private void skycustomizer$onActionbar(Component message, boolean bl, CallbackInfo ci) {
        HudCustomizer.rememberMessage(message);
        try {
            if (VisualsCustomizer.shouldHideActionbar()) {
                ci.cancel();
            }
        } catch (Exception ignored) {}
    }

    @Inject(method = "extractOverlayMessage", at = @At("HEAD"), cancellable = true)
    private void skycustomizer$hideEditingActionbar(GuiGraphicsExtractor graphics, DeltaTracker delta, CallbackInfo ci) {
        if (Minecraft.getInstance().screen instanceof HudEditorScreen || VisualsCustomizer.shouldHideActionbar()) {
            ci.cancel();
        }
    }

    @WrapOperation(method = "extractOverlayMessage", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;textWithBackdrop(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIII)V"))
    private void skycustomizer$positionActionbar(GuiGraphicsExtractor graphics, Font font, Component message,
                                                int x, int y, int messageWidth, int color, Operation<Void> original) {
        if (!HudCustomizer.isCustomHudEnabled()) {
            original.call(graphics, font, message, x, y, messageWidth, color);
            return;
        }
        var c = SkyCustomizerConfig.get().visuals;

        var bounds = HudLayout.actionbar(graphics.guiWidth(), graphics.guiHeight(), messageWidth, 9,
                c.hudScale, c.hudOffsetX, c.hudOffsetY);
        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate(bounds.x() - graphics.guiWidth() / 2,
                    bounds.y() - (graphics.guiHeight() - 68));
            graphics.pose().scale(bounds.scale(), bounds.scale());
            original.call(graphics, font, message, 0, 0, messageWidth, color);
        } finally {
            graphics.pose().popMatrix();
        }
    }
}
