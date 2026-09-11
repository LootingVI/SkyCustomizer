package de.flori.skycustomizer.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import de.flori.skycustomizer.features.ArmorCustomizer;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EquipmentLayerRenderer.class)
public abstract class MixinEquipmentLayerRenderer {

    @WrapOperation(
            method = "renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;II)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/component/DyedItemColor;getOrDefault(Lnet/minecraft/world/item/ItemStack;I)I")
    )
    private int skycustomizer$armorColor(ItemStack stack, int fallback, Operation<Integer> original) {
        try {
            return ArmorCustomizer.getRenderColor(stack, original.call(stack, fallback));
        } catch (Exception e) {
            return original.call(stack, fallback);
        }
    }
}
