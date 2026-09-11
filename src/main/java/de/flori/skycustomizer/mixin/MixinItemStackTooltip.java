package de.flori.skycustomizer.mixin;

import de.flori.skycustomizer.features.ItemCustomizer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.List;

@Mixin(ItemStack.class)
public class MixinItemStackTooltip {
    @Inject(method = "getTooltipLines", at = @At("RETURN"), cancellable = true)
    private void skycustomizer$tooltip(Item.TooltipContext context, Player player, TooltipFlag flag,
                                       CallbackInfoReturnable<List<Component>> cir) {
        try {
            List<Component> original = cir.getReturnValue();
            List<Component> result = ItemCustomizer.customizeTooltip((ItemStack) (Object) this, original);
            if (result != original) cir.setReturnValue(result);
        } catch (Exception ignored) {}
    }

    @Inject(method = "getHoverName", at = @At("RETURN"), cancellable = true)
    private void skycustomizer$name(CallbackInfoReturnable<Component> cir) {
        try {
            Component original = cir.getReturnValue();
            Component result = ItemCustomizer.customizeName((ItemStack) (Object) this, original);
            if (result != original) cir.setReturnValue(result);
        } catch (Exception ignored) {}
    }

    @Inject(method = "hasFoil", at = @At("RETURN"), cancellable = true)
    private void skycustomizer$glint(CallbackInfoReturnable<Boolean> cir) {
        boolean original = cir.getReturnValue();
        boolean result = ItemCustomizer.customizeGlint(original);
        if (result != original) cir.setReturnValue(result);
    }
}
