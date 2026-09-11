package de.flori.skycustomizer.features;

import de.flori.skycustomizer.config.SkyCustomizerConfig;
import de.flori.skycustomizer.util.ColorUtil;
import de.flori.skycustomizer.util.NametagRules;
import de.flori.skycustomizer.util.TextUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.ArrayList;
import java.util.Optional;

public final class NameCustomizer {
    private NameCustomizer() {}
    private static String cachedUsername;
    private static Component cachedName;
    private static long cachedRevision = -1, cachedFrame = -1;

    public static synchronized Component customizeName(String originalUsername) {
        var cfg = SkyCustomizerConfig.get();
        long revision = SkyCustomizerConfig.runtimeRevision();
        long frame = cfg.name.chroma ? System.nanoTime() / 33_000_000L : 0;
        if (cachedName == null || revision != cachedRevision || frame != cachedFrame
                || !originalUsername.equals(cachedUsername)) {
            cachedName = TextUtil.applyNameCustomization(originalUsername);
            cachedUsername = originalUsername;
            cachedRevision = revision;
            cachedFrame = frame;
        }
        return cachedName.copy();
    }

    public static Component customizePlayerNametag(Component original, String username, boolean self) {
        if (original == null) return null;
        var cfg = SkyCustomizerConfig.get();
        if (!cfg.enabled) return original;
        Component result = LevelCustomizer.processNametag(TextUtil.parseSectionFormatting(original));
        if (!cfg.name.enabled || !cfg.name.applyOverHead) return result;
        var rule = NametagRules.match(cfg.name.rules, username, self, NametagRules.level(result));
        if (rule != null && rule.hidden) return null;
        Component displayName = self ? customizeName(username) : otherPlayerName(username);
        Component customized = rule != null ? NametagRules.apply(result, username,
                displayName == null ? Component.literal(username) : displayName, rule)
                : displayName == null ? result : replaceUsername(result, username, displayName, false);
        return decorateNametag(customized, username);
    }

    private static Component otherPlayerName(String username) {
        var name = SkyCustomizerConfig.get().name;
        String alias = name.playerAliases.get(username);
        if (alias == null) {
            for (var entry : name.playerAliases.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(username)) {
                    alias = entry.getValue();
                    break;
                }
            }
        }
        if ((alias == null || alias.isEmpty()) && !name.recolorOtherPlayers) return null;
        Style style = name.recolorOtherPlayers
                ? Style.EMPTY.withColor(ColorUtil.fromHex(name.otherPlayerColorHex) & 0xFFFFFF) : Style.EMPTY;
        return TextUtil.parseLegacyFormatting(alias == null || alias.isEmpty() ? username : alias, style);
    }

    public static int usernameStart(String text, String username) {
        return usernameStart(text, username, 0);
    }

    private static int usernameStart(String text, String username, int from) {
        if (text == null || username == null || username.isEmpty()) return -1;
        for (int index = from, limit = text.length() - username.length(); index <= limit; index++) {
            int end = index + username.length();
            if (text.regionMatches(true, index, username, 0, username.length())
                    && (index == 0 || !nameChar(text.codePointBefore(index)))
                    && (end == text.length() || !nameChar(text.codePointAt(end)))) return index;
        }
        return -1;
    }

    private static boolean nameChar(int c) { return Character.isLetterOrDigit(c) || c == '_'; }

    public static Component customizeScoreboard(Component original, String username) {
        var cfg = SkyCustomizerConfig.get();
        if (!cfg.enabled || !cfg.name.enabled || !cfg.name.applyInScoreboard) return original;
        return replaceUsername(original, username, customizeName(username), false);
    }

    public static Component customizeNametag(Component original, String username) {
        return customizePlayerNametag(original, username, true);
    }

    public static Component fadeNametag(Component original, float amount) {
        if (original == null || amount <= 0) return original;
        float fade = Math.clamp(amount, 0, 1);
        MutableComponent result = Component.empty();
        original.visit((style, text) -> {
            TextColor color = style.getColor();
            int rgb = color == null ? 0xFFFFFF : color.getValue();
            int r = Math.round(((rgb >>> 16) & 255) * (1 - fade) + 36 * fade);
            int g = Math.round(((rgb >>> 8) & 255) * (1 - fade) + 29 * fade);
            int b = Math.round((rgb & 255) * (1 - fade) + 23 * fade);
            result.append(Component.literal(text).withStyle(style.withColor((r << 16) | (g << 8) | b)));
            return Optional.empty();
        }, Style.EMPTY);
        return result;
    }

    private static Component decorateNametag(Component tag, String username) {
        var name = SkyCustomizerConfig.get().name;
        if (tag == null || username == null || username.isBlank()) return tag;
        String prefix = "";
        String styleColor = "";
        if (containsIgnoreCase(name.friendNames, username)) {
            prefix += name.friendPrefix == null ? "" : name.friendPrefix;
            styleColor = name.friendColorHex;
        } else if (containsIgnoreCase(name.guildNames, username)) {
            prefix += name.guildPrefix == null ? "" : name.guildPrefix;
            styleColor = name.guildColorHex;
        }
        if (name.nametagHeadIcon && name.nametagHeadSymbol != null && !name.nametagHeadSymbol.isBlank()) {
            prefix += name.nametagHeadSymbol + " ";
        }
        String badge = lookupIgnoreCase(name.playerBadges, username);
        if (badge != null && !badge.isBlank()) prefix += badge + " ";
        String source = tag.getString();
        for (var entry : name.rankIcons.entrySet()) {
            if (entry.getKey() != null && !entry.getKey().isBlank() && entry.getValue() != null && !entry.getValue().isBlank()
                    && source.toLowerCase(java.util.Locale.ROOT).contains(entry.getKey().toLowerCase(java.util.Locale.ROOT))) {
                prefix += entry.getValue() + " ";
                break;
            }
        }
        int gap = Math.clamp(name.nametagSpacing, 0, 4);
        if (gap > 0 && !prefix.isEmpty()) prefix += " ".repeat(gap);
        Component result = tag;
        if (!styleColor.isBlank()) {
            result = TextUtil.recolorRange(result, 0, result.getString().length(), ColorUtil.fromHex(styleColor) & 0xFFFFFF);
        }
        if (name.nametagPill) {
            Style pill = Style.EMPTY.withColor(ColorUtil.fromHex(name.nametagPillColorHex) & 0xFFFFFF);
            result = Component.literal("❬ ").withStyle(pill).append(result)
                    .append(Component.literal(" ❭").withStyle(pill));
        }
        return prefix.isEmpty() ? result : TextUtil.parseLegacyFormatting(prefix, Style.EMPTY).append(result);
    }

    private static boolean containsIgnoreCase(java.util.List<String> names, String username) {
        if (names == null) return false;
        return names.stream().anyMatch(name -> name != null && name.equalsIgnoreCase(username));
    }

    private static String lookupIgnoreCase(java.util.Map<String, String> values, String username) {
        if (values == null) return null;
        for (var entry : values.entrySet()) if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(username)) return entry.getValue();
        return null;
    }

    public static Component customizeTabName(Component original, String username) {
        var cfg = SkyCustomizerConfig.get();
        Component result = LevelCustomizer.processNametag(original);
        if (!cfg.enabled || !cfg.name.enabled || !cfg.name.applyInTab) return result;
        return replaceUsername(result, username, customizeName(username), false);
    }

    public static Component customizeChat(Component original, String username) {
        var cfg = SkyCustomizerConfig.get();
        if (original == null || !cfg.enabled || !cfg.name.enabled || !cfg.name.applyInChat
                || username == null || username.isEmpty()) return original;
        Component result = TextUtil.parseSectionFormatting(original);
        if (usernameStart(result.getString(), username) < 0) return original;
        return replaceUsername(result, username, customizeName(username), true);
    }

    public static Component replaceUsername(Component original, String username, Component replacement, boolean all) {
        if (original == null) return null;
        String text = original.getString();
        int first = usernameStart(text, username);
        if (first < 0) return original;
        var starts = new ArrayList<Integer>();
        starts.add(first);
        if (all) {
            for (int next = usernameStart(text, username, first + username.length()); next >= 0;
                 next = usernameStart(text, username, next + username.length())) starts.add(next);
        }
        var result = Component.empty();
        int[] position = {0}, match = {0};
        original.visit((style, segment) -> {
            int segmentStart = position[0], offset = 0;
            while (offset < segment.length()) {
                int global = segmentStart + offset;
                if (match[0] >= starts.size()) {
                    result.append(Component.literal(segment.substring(offset)).withStyle(style));
                    break;
                }
                int start = starts.get(match[0]), end = start + username.length();
                if (global >= end) { match[0]++; continue; }
                if (global < start) {
                    int until = Math.min(segment.length(), start - segmentStart);
                    result.append(Component.literal(segment.substring(offset, until)).withStyle(style));
                    offset = until;
                } else {
                    if (global == start) result.append(replacement.copy().withStyle(replacement.getStyle().applyTo(style)));
                    offset = Math.min(segment.length(), end - segmentStart);
                }
            }
            position[0] += segment.length();
            return Optional.empty();
        }, Style.EMPTY);
        return result;
    }
}
