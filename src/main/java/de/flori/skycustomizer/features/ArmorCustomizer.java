package de.flori.skycustomizer.features;

import de.flori.skycustomizer.config.SkyCustomizerConfig;
import de.flori.skycustomizer.util.ColorUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;

public final class ArmorCustomizer {

    private ArmorCustomizer() {}

    public enum ArmorPiece {
        HELMET, CHESTPLATE, LEGGINGS, BOOTS
    }

    private static int getCustomColor(ArmorPiece piece) {
        SkyCustomizerConfig cfg = SkyCustomizerConfig.get();

        String hex;
        if (cfg.armor.perPiece) {
            hex = switch (piece) {
                case HELMET -> cfg.armor.helmetHex;
                case CHESTPLATE -> cfg.armor.chestplateHex;
                case LEGGINGS -> cfg.armor.leggingsHex;
                case BOOTS -> cfg.armor.bootsHex;
            };
        } else {
            hex = cfg.armor.globalHex;
        }

        if (cfg.armor.chroma) {
            float offset = switch (piece) {
                case HELMET -> 0.0f;
                case CHESTPLATE -> 0.25f;
                case LEGGINGS -> 0.5f;
                case BOOTS -> 0.75f;
            };
            return ColorUtil.chroma(offset, cfg.armor.chromaSpeed) & 0xFFFFFF;
        }

        return ColorUtil.fromHex(hex) & 0xFFFFFF;
    }

    public static int getRenderColor(ItemStack stack, int originalArgb) {
        SkyCustomizerConfig cfg = SkyCustomizerConfig.get();
        if (!cfg.enabled || !cfg.armor.enabled) return originalArgb;

        if (!stack.getComponents().has(DataComponents.DYED_COLOR)) return originalArgb;

        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        if (equippable == null) return originalArgb;

        ArmorPiece piece = switch (equippable.slot()) {
            case HEAD -> ArmorPiece.HELMET;
            case CHEST -> ArmorPiece.CHESTPLATE;
            case LEGS -> ArmorPiece.LEGGINGS;
            case FEET -> ArmorPiece.BOOTS;
            default -> null;
        };
        if (piece == null) return originalArgb;

        int rgb = getCustomColor(piece);
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }
}
