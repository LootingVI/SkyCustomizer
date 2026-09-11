package de.flori.skycustomizer.util;

import com.google.gson.Gson;
import de.flori.skycustomizer.config.SkyCustomizerConfig.ItemConfig;
import de.flori.skycustomizer.config.SkyCustomizerConfig.LevelConfig;
import de.flori.skycustomizer.features.NameCustomizer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import java.util.*;

final class CustomizationChecks {
    private static int assertions;
    static void run() {
        Component legacy = TextUtil.parseLegacyFormatting("&aAlex §bBlue &#12AB34&lRGB&r!", Style.EMPTY);
        check(legacy.getString().equals("Alex Blue RGB!"), "Formatting codes must never become visible letters");
        check(styleAt(legacy, 0).getColor().getValue() == 0x55FF55, "Legacy green");
        check(styleAt(legacy, 5).getColor().getValue() == 0x55FFFF, "Section-sign aqua");
        check(styleAt(legacy, 10).getColor().getValue() == 0x12AB34 && styleAt(legacy, 10).isBold(), "RGB and bold");
        check(!styleAt(legacy, 13).isBold(), "Reset ends bold");
        check(TextUtil.stripLegacyFormatting("&aA§lB&#112233C§#445566D").equals("ABCD"), "Strip legacy and RGB");
        check(TextUtil.parseLegacyFormatting("R&Z &#zzzzzz", Style.EMPTY).getString().equals("R&Z &#zzzzzz"), "Invalid codes remain literal");

        var source = Component.literal("Prefix ").withColor(0x334455)
                .append(Component.literal("Alex").withStyle(Style.EMPTY.withItalic(true).withColor(0x998877)))
                .append(Component.literal(" tail").withStyle(Style.EMPTY.withBold(true).withColor(0x123456)));
        Component recolored = TextUtil.recolorRange(source, 7, 11, 0xABCDEF);
        check(recolored.getString().equals(source.getString()), "Recolor preserves text");
        check(styleAt(recolored, 0).getColor().getValue() == 0x334455, "Prefix color survives");
        check(styleAt(recolored, 7).isItalic() && styleAt(recolored, 7).getColor().getValue() == 0xABCDEF, "Name style survives");
        check(styleAt(recolored, 12).isBold() && styleAt(recolored, 12).getColor().getValue() == 0x123456, "Tail style survives");
        Component longText = TextUtil.recolorRange(Component.literal("x".repeat(2000)), 100, 104, 0xFFFFFF);
        check(longText.getSiblings().size() <= 3, "Recolor must split segments, not every character");

        var cache = new ComponentCache();
        var key = Component.literal("original").append(Component.literal("child"));
        var equivalent = key.copy();
        cache.put(key, recolored, 7, 0);
        check(cache.get(equivalent, 7, 0) != null, "Equivalent component hits cache");
        check(cache.get(equivalent, 8, 0) == null && cache.get(equivalent, 7, 1) == null, "Config and animation invalidate cache");
        check(cache.get(equivalent.copy().withColor(0x123456), 7, 0) == null, "Style-sensitive key");
        cache.get(equivalent, 7, 0).getSiblings().clear();
        check(cache.get(equivalent, 7, 0).getString().equals(source.getString()), "Returned values are isolated");
        ((net.minecraft.network.chat.MutableComponent) key.getSiblings().getFirst()).append("changed");
        check(cache.get(Component.literal("original").append(Component.literal("child")), 7, 0) != null, "Nested key mutation is isolated");

        var unique = ItemIdentity.fromParts("abc", "SWORD", "minecraft:diamond_sword", "Sword");
        check(unique.unique() && unique.key().equals("uuid:abc"), "Unique server item ID wins");
        check(unique.key().equals(ItemIdentity.fromParts("abc", "SWORD", "minecraft:diamond_sword", "Renamed").key()), "Unique ID survives rename");
        var type = ItemIdentity.fromParts("", "SWORD", "minecraft:diamond_sword", "Sword");
        check(!type.unique() && type.key().equals("skyblock:SWORD"), "Fallback explicitly targets matching type");
        check(!ItemIdentity.fromParts("", "", "minecraft:stone", "Stone").key()
                .equals(ItemIdentity.fromParts("", "", "minecraft:dirt", "Stone").key()), "Vanilla item types stay separate");
        check(RegexCache.compile("[broken") == null && RegexCache.compile("[broken") == null, "Invalid regex is safe");
        check(RegexCache.compile("Sword.*") == RegexCache.compile("Sword.*"), "Regex compilation reused");
        check(NameCustomizer.usernameStart("[120] Alex: hello", "Alex") == 6, "Exact username found");
        check(NameCustomizer.usernameStart("Alexander Alex_2", "Alex") == -1, "Do not rename substrings of other names");

        LevelConfig levels = new LevelConfig();
        levels.customColors = new LinkedHashMap<>(Map.of(0, "#000000", 100, "#FFFFFF"));
        levels.gradientBetweenLevels = false;
        check(levels.getColorForLevel(50).equals("#000000"), "Threshold floor");
        Object sorted = levels.customColors;
        check(levels.getColorForLevel(150).equals("#FFFFFF") && levels.customColors == sorted, "Sorted map is reused");
        levels.gradientBetweenLevels = true;
        int midpoint = ColorUtil.fromHex(levels.getColorForLevel(50)) & 0xFF;
        check(midpoint >= 127 && midpoint <= 128, "Interpolated midpoint");

        ItemConfig saved = new ItemConfig();
        saved.heldItemNames.put(unique.key(), "&#FFAA00&lMy Sword");
        saved.heldItemLore.put(unique.key(), "&7First\\n&bSecond");
        saved.heldItemLabels.put(unique.key(), "Sword");
        Gson gson = new Gson();
        ItemConfig draft = gson.fromJson(gson.toJson(saved), ItemConfig.class);
        check(draft.heldItemNames.equals(saved.heldItemNames) && draft.heldItemLore.equals(saved.heldItemLore)
                && draft.heldItemLabels.equals(saved.heldItemLabels), "Stored item overrides round-trip");
        draft.heldItemNames.clear();
        check(!saved.heldItemNames.isEmpty(), "Discarded item draft does not alter saved config");
        System.out.println("Customization: " + assertions + " checks passed.");
    }
    private static Style styleAt(Component component, int position) {
        int[] index = {0};
        return component.visit((style, text) -> {
            int start = index[0]; index[0] += text.length();
            return position >= start && position < index[0] ? Optional.of(style) : Optional.<Style>empty();
        }, Style.EMPTY).orElseThrow();
    }
    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
