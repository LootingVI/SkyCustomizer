package de.flori.skycustomizer.features;

import de.flori.skycustomizer.config.SkyCustomizerConfig;
import de.flori.skycustomizer.util.ColorUtil;
import de.flori.skycustomizer.util.NametagRules;
import de.flori.skycustomizer.util.TextUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

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
        if (rule != null) return NametagRules.apply(result, username,
                displayName == null ? Component.literal(username) : displayName, rule);
        return displayName == null ? result : replaceUsername(result, username, displayName, false);
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
