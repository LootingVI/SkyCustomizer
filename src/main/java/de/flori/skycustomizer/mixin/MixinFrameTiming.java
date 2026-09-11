package de.flori.skycustomizer.mixin;

import de.flori.skycustomizer.config.StudioScreen;
import de.flori.skycustomizer.util.FrameTimings;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MixinFrameTiming {
    @Inject(method = "runTick", at = @At("HEAD"))
    private void skycustomizer$sampleFrame(boolean renderLevel, CallbackInfo ci) {
        if (!FrameTimings.recording()) return;
        Minecraft mc = (Minecraft)(Object)this;
        boolean menu = mc.screen instanceof StudioScreen;
        FrameTimings.frame(System.nanoTime(), menu, renderLevel && mc.level != null && (menu || mc.screen == null));
    }
}
