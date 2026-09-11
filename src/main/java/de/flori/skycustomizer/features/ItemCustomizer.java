package de.flori.skycustomizer.features;

import de.flori.skycustomizer.config.SkyCustomizerConfig;
import de.flori.skycustomizer.util.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;
import java.util.*;
import java.util.regex.Pattern;

public final class ItemCustomizer {
    private static final Pattern RARITY = Pattern.compile("(?:^|\\s)(VERY SPECIAL|SPECIAL|DIVINE|MYTHIC|LEGENDARY|EPIC|RARE|UNCOMMON|COMMON)(?:\\s|$)");
    private record CachedName(Component input, Component output, String identity, Object lore, long revision, long frame) {}
    private static final Map<ItemStack, CachedName> NAMES = new WeakHashMap<>();
    private ItemCustomizer() {}

    public static synchronized Component customizeName(ItemStack stack, Component original) {
        var cfg = SkyCustomizerConfig.get();
        var settings = cfg.items;
        if (!cfg.enabled || !settings.enabled || original == null || stack.isEmpty()) return original;
        boolean overrides = settings.namesEnabled && (!settings.heldItemNames.isEmpty()
                || !settings.customNames.isEmpty() || !settings.regexRules.isEmpty() || !settings.itemTypeNames.isEmpty());
        boolean decorations = settings.itemIconsEnabled || (settings.favoriteMarkerEnabled && !settings.favoriteItemTypes.isEmpty());
        if (!overrides && !settings.rarityColorsEnabled && !decorations) return LevelCustomizer.processComponent(original);
        var identity = ItemIdentity.of(stack);
        long revision = SkyCustomizerConfig.runtimeRevision();
        Object loreData = settings.rarityColorsEnabled ? stack.get(DataComponents.LORE) : null;
        long frame = cfg.level.chromaFor600Plus ? System.nanoTime() / 33_000_000L : 0;
        CachedName cached = NAMES.get(stack);
        if (cached != null && cached.revision == revision && cached.frame == frame
                && cached.lore == loreData && cached.identity.equals(identity.key()) && cached.input.equals(original)) return cached.output.copy();
        String raw = original.getString();
        String renamed = overrides ? settings.heldItemNames.get(identity.key()) : null;
        String itemType = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        if (renamed == null && settings.namesEnabled) {
            String typeName = settings.itemTypeNames.get(itemType);
            if (typeName != null && !typeName.isBlank()) renamed = typeName.replace("{original}", raw);
        }
        if (overrides && renamed == null) {
            for (var entry : settings.customNames.entrySet()) {
                Pattern pattern = RegexCache.compile(entry.getKey());
                if (pattern == null || entry.getValue() == null || entry.getValue().isEmpty()) continue;
                var matcher = pattern.matcher(raw);
                if (!matcher.find()) continue;
                try {
                    renamed = entry.getValue().contains("{original}") ? entry.getValue().replace("{original}", raw)
                            : matcher.replaceAll(entry.getValue());
                } catch (IllegalArgumentException | IndexOutOfBoundsException ignored) {}
                if (renamed != null) break;
            }
            if (renamed == null) {
                String text = raw;
                for (var rule : settings.regexRules) {
                    if (rule == null || !rule.enabled) continue;
                    Pattern pattern = RegexCache.compile(rule.pattern);
                    if (pattern == null) continue;
                    try { text = pattern.matcher(text).replaceAll(rule.replacement == null ? "" : rule.replacement); }
                    catch (IllegalArgumentException | IndexOutOfBoundsException ignored) {}
                }
                if (!text.equals(raw)) renamed = text;
            }
        }
        Component result = renamed == null ? original
                : TextUtil.parseLegacyFormatting(renamed.replace("{original}", raw), original.getStyle());
        if (settings.rarityColorsEnabled && renamed == null) {
            var lore = stack.get(DataComponents.LORE);
            if (lore != null) {
                for (int i = lore.lines().size() - 1; i >= Math.max(0, lore.lines().size() - 4); i--) {
                    var match = RARITY.matcher(TextUtil.stripLegacyFormatting(lore.lines().get(i).getString()).toUpperCase(Locale.ROOT));
                    if (match.find()) {
                        String hex = settings.rarityColors.get(match.group(1).replace(' ', '_'));
                        if (hex != null) result = TextUtil.recolorRange(result, 0, result.getString().length(), ColorUtil.fromHex(hex) & 0xFFFFFF);
                        break;
                    }
                }
            }
        }
        if (settings.itemIconsEnabled && settings.itemIcon != null && !settings.itemIcon.isBlank()) {
            result = Component.literal(settings.itemIcon + " ").append(result);
        }
        if (settings.favoriteMarkerEnabled && settings.favoriteItemTypes.contains(itemType)
                && settings.favoriteMarker != null && !settings.favoriteMarker.isBlank()) {
            result = Component.literal(settings.favoriteMarker + " ").withStyle(Style.EMPTY.withColor(0xFFAA00)).append(result);
        }
        result = LevelCustomizer.processComponent(result);
        if (NAMES.size() >= 512) NAMES.clear();
        NAMES.put(stack, new CachedName(original.copy(), result.copy(), identity.key(), loreData, revision, frame));
        return result;
    }

    public static List<Component> customizeTooltip(ItemStack stack, List<Component> original) {
        var cfg = SkyCustomizerConfig.get();
        var s = cfg.items;
        if (!cfg.enabled || !s.enabled || original == null || original.isEmpty()) return original;
        boolean customLore = s.loreEnabled && (!s.heldItemLore.isEmpty() || !s.customLore.isEmpty());
        if (!s.hideLore && !s.showOriginalName && !customLore && !s.tooltipStyleEnabled) return original;
        String rawName = ItemIdentity.originalName(stack).getString();
        String extra = customLore ? s.heldItemLore.get(ItemIdentity.of(stack).key()) : null;
        if (customLore && extra == null) {
            for (var entry : s.customLore.entrySet()) {
                if (!entry.getKey().isEmpty() && rawName.contains(entry.getKey())) { extra = entry.getValue(); break; }
            }
        }
        boolean originalLabel = s.showOriginalName && !rawName.equals(original.getFirst().getString());
        if (!s.hideLore && !originalLabel && (extra == null || extra.isEmpty()) && !s.tooltipStyleEnabled) return original;
        List<Component> result = new ArrayList<>();
        result.add(original.getFirst());
        if (originalLabel) result.add(Component.literal("Original: " + rawName).withColor(0x888888));
        if (extra != null && !extra.isEmpty()) {
            for (String line : extra.replace("\\n", "\n").split("\n", -1)) {
                result.add(TextUtil.parseLegacyFormatting(line, Style.EMPTY.withColor(0xAAAAAA)));
            }
        }
        if (s.tooltipStyleEnabled) {
            String rarity = rarity(stack);
            String accent = s.tooltipAccentHex == null || s.tooltipAccentHex.isBlank() ? "#FFAA00" : s.tooltipAccentHex;
            int color = ColorUtil.fromHex(accent) & 0xFFFFFF;
            result.add(1, Component.literal("━━━━━━━━ " + rarity + " ━━━━━━━━").withColor(color));
            result.add(Component.literal("━━━━━━━━━━━━━━━━━━").withColor(color));
        }
        if (!s.hideLore) result.addAll(original.subList(1, original.size()));
        return result;
    }

    public static boolean customizeGlint(boolean original) {
        var cfg = SkyCustomizerConfig.get();
        if (!cfg.enabled || !cfg.items.enabled) return original;
        return !cfg.items.hideGlint && (cfg.items.forceGlint || original);
    }

    private static String rarity(ItemStack stack) {
        var lore = stack.get(DataComponents.LORE);
        if (lore == null) return "CUSTOM";
        for (int i = lore.lines().size() - 1; i >= Math.max(0, lore.lines().size() - 4); i--) {
            var match = RARITY.matcher(TextUtil.stripLegacyFormatting(lore.lines().get(i).getString()).toUpperCase(Locale.ROOT));
            if (match.find()) return match.group(1);
        }
        return "CUSTOM";
    }

}
