package de.flori.skycustomizer.util;

import de.flori.skycustomizer.config.SkyCustomizerConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TextUtil {

    private TextUtil() {}

    public static final Pattern LEVEL_PATTERN = Pattern.compile(
            "\\[\\s*[★✫✪✥]*\\s*(\\d{1,5})\\s*\\]"
    );
    private static final Pattern LEGACY_LEVEL_BADGE = Pattern.compile(
            "§8\\[(?:§[0-9a-fk-or])*(?<stars>[★✫✪✥]*)\\s*(?:§[0-9a-fk-or])*(?<level>\\d{1,5})(?:§[0-9a-fk-or])*\\]"
    );

    private static final int DARK_RED_RGB = 0xAA0000;

    private static final int DARK_GRAY_RGB = 0x555555;

    private static void dbg(String msg) {
        if (SkyCustomizerConfig.get().debugMode) {
            System.out.println("[SkyCustomizer|Level] " + msg);
        }
    }

    public static MutableComponent applyNameCustomization(String originalName) {
        SkyCustomizerConfig cfg = SkyCustomizerConfig.get();
        if (!cfg.enabled || !cfg.name.enabled) return Component.literal(originalName);

        int rgb = ColorUtil.fromHex(cfg.name.colorHex) & 0xFFFFFF;
        Style baseStyle = Style.EMPTY
                .withColor(TextColor.fromRgb(rgb))
                .withBold(cfg.name.bold)
                .withItalic(cfg.name.italic)
                .withUnderlined(cfg.name.underline)
                .withStrikethrough(cfg.name.strikethrough)
                .withObfuscated(cfg.name.obfuscated);

        String customName = valueOrEmpty(cfg.name.customName);
        String displayName = cfg.name.useCustomName && !customName.isEmpty()
                ? customName : originalName;
        String full = valueOrEmpty(cfg.name.prefix) + displayName + valueOrEmpty(cfg.name.suffix);

        if (cfg.name.chroma) {
            return createChromaComponent(stripLegacyFormatting(full), cfg.name.chromaSpeed, baseStyle);
        }
        if (cfg.name.rainbow) {
            return createRainbowComponent(stripLegacyFormatting(full), baseStyle);
        }
        return parseLegacyFormatting(full, baseStyle);
    }

    public static MutableComponent parseLegacyFormatting(String text, Style baseStyle) {
        return parseLegacyFormatting(text, baseStyle, true);
    }

    public static Component parseSectionFormatting(Component original) {
        if (original == null || original.getString().indexOf('\u00a7') < 0) return original;
        var result = Component.empty();
        original.visit((style, text) -> {
            result.append(parseLegacyFormatting(text, style, false));
            return Optional.empty();
        }, Style.EMPTY);
        return result;
    }

    public static String editableFormatting(Component original) {
        var result = new StringBuilder();
        parseSectionFormatting(original).visit((style, text) -> {
            if (text.isEmpty()) return Optional.empty();
            result.append("&r");
            if (style.getColor() != null) result.append(String.format(java.util.Locale.ROOT, "&#%06X", style.getColor().getValue() & 0xFFFFFF));
            if (style.isBold()) result.append("&l");
            if (style.isItalic()) result.append("&o");
            if (style.isUnderlined()) result.append("&n");
            if (style.isStrikethrough()) result.append("&m");
            if (style.isObfuscated()) result.append("&k");
            result.append(text);
            return Optional.empty();
        }, Style.EMPTY);
        return result.toString();
    }

    private static MutableComponent parseLegacyFormatting(String text, Style baseStyle, boolean ampersands) {
        if (text == null || text.isEmpty()) return Component.empty();
        MutableComponent result = Component.empty();
        Style currentStyle = baseStyle;
        StringBuilder visible = new StringBuilder();

        for (int i = 0; i < text.length(); i++) {
            char current = text.charAt(i);
            if ((current == '\u00a7' || ampersands && current == '&') && isHexSequence(text, i)) {
                appendStyled(result, visible, currentStyle);
                currentStyle = baseStyle.withColor(Integer.parseInt(text.substring(i + 2, i + 8), 16));
                i += 7;
                continue;
            }
            if ((current == '\u00a7' || ampersands && current == '&') && i + 1 < text.length()) {
                char code = Character.toLowerCase(text.charAt(i + 1));
                if (isLegacyFormatCode(code)) {
                    appendStyled(result, visible, currentStyle);
                    currentStyle = styleForLegacyCode(currentStyle, baseStyle, code);
                    i++;
                    continue;
                }
            }
            visible.append(current);
        }
        appendStyled(result, visible, currentStyle);
        return result;
    }

    public static String stripLegacyFormatting(String text) {
        if (text == null) return "";
        StringBuilder visible = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char current = text.charAt(i);
            if ((current == '\u00a7' || current == '&') && isHexSequence(text, i)) {
                i += 7;
                continue;
            }
            if ((current == '\u00a7' || current == '&') && i + 1 < text.length()
                    && isLegacyFormatCode(Character.toLowerCase(text.charAt(i + 1)))) {
                i++;
            } else {
                visible.append(current);
            }
        }
        return visible.toString();
    }

    private static boolean isHexSequence(String text, int index) {
        if (index + 7 >= text.length() || text.charAt(index + 1) != '#') return false;
        for (int i = index + 2; i < index + 8; i++) if (Character.digit(text.charAt(i), 16) < 0) return false;
        return true;
    }

    private static void appendStyled(MutableComponent result, StringBuilder text, Style style) {
        if (!text.isEmpty()) {
            result.append(Component.literal(text.toString()).withStyle(style));
            text.setLength(0);
        }
    }

    private static Style styleForLegacyCode(Style currentStyle, Style baseStyle, char code) {
        int color = legacyColor(code);
        if (color >= 0) {

            return baseStyle.withColor(TextColor.fromRgb(color));
        }
        return switch (code) {
            case 'k' -> currentStyle.withObfuscated(true);
            case 'l' -> currentStyle.withBold(true);
            case 'm' -> currentStyle.withStrikethrough(true);
            case 'n' -> currentStyle.withUnderlined(true);
            case 'o' -> currentStyle.withItalic(true);
            case 'r' -> baseStyle;
            default -> currentStyle;
        };
    }

    private static boolean isLegacyFormatCode(char code) {
        return legacyColor(code) >= 0 || "klmnor".indexOf(code) >= 0;
    }

    private static int legacyColor(char code) {
        return switch (code) {
            case '0' -> 0x000000;
            case '1' -> 0x0000AA;
            case '2' -> 0x00AA00;
            case '3' -> 0x00AAAA;
            case '4' -> 0xAA0000;
            case '5' -> 0xAA00AA;
            case '6' -> 0xFFAA00;
            case '7' -> 0xAAAAAA;
            case '8' -> 0x555555;
            case '9' -> 0x5555FF;
            case 'a' -> 0x55FF55;
            case 'b' -> 0x55FFFF;
            case 'c' -> 0xFF5555;
            case 'd' -> 0xFF55FF;
            case 'e' -> 0xFFFF55;
            case 'f' -> 0xFFFFFF;
            default -> -1;
        };
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    public static Component recolorLevelPrefix(Component original, boolean playerLabel) {
        if (original == null) return null;

        SkyCustomizerConfig cfg = SkyCustomizerConfig.get();
        if (!cfg.enabled || !cfg.level.enabled) {
            dbg("skip: mod or level disabled");
            return original;
        }

        String raw = original.getString();
        if (raw == null || raw.isEmpty()) return original;

        Component base = original;
        boolean legacyBadge = false;
        if (raw.indexOf('\u00a7') >= 0) {
            Component conv = playerLabel ? parseSectionFormatting(original) : convertLegacyBadge(original);
            if (conv != null) {
                base = conv;
                legacyBadge = true;
            } else {

                return original;
            }
        }

        String text = base.getString();
        Matcher matcher = LEVEL_PATTERN.matcher(text);
        if (!matcher.find()) {
            if (cfg.debugMode) dbg("no level badge in: " + truncate(text));
            return original;
        }

        int level;
        try {
            level = Integer.parseInt(matcher.group(1));
        } catch (Exception e) {
            return original;
        }

        int bracketOpen  = text.indexOf('[', matcher.start());
        int bracketClose = text.indexOf(']', matcher.start());
        if (bracketOpen < 0 || bracketClose < 0 || bracketClose <= bracketOpen) return original;

        int digitStart = matcher.start(1);
        int digitEnd = matcher.end(1);

        if (!legacyBadge && !playerLabel) {
            boolean darkRed = isDarkRed(styleAt(base, digitStart));
            boolean atStart = matcher.start() == 0;
            if (!darkRed && !atStart) {
                if (cfg.debugMode) dbg("level " + level + ": not a level badge (style="
                        + styleColorHex(base, digitStart) + ", atStart=" + atStart + "), skipping");
                return original;
            }
        }

        if (cfg.level.chromaFor600Plus && level >= 600) {
            if (cfg.debugMode) dbg("chroma level " + level);
            return buildChromaLevel(base, level, digitStart, digitEnd, bracketOpen, bracketClose,
                    cfg.level.chromaSpeed, cfg.level.bracketColorHex);
        }

        String levelHex;
        if (!cfg.level.overrideHypixelColors && level < 500) {
            levelHex = LevelColorUtil.sectionToHex(LevelColorUtil.getOriginalColorForLevel(level));
        } else {
            levelHex = cfg.level.getColorForLevel(level);
        }
        if (levelHex == null || levelHex.isBlank() || "CHROMA".equalsIgnoreCase(levelHex)) return original;

        if (cfg.debugMode) dbg("level " + level + " -> " + levelHex);
        return buildLevel(base, level, digitStart, digitEnd, bracketOpen, bracketClose,
                ColorUtil.fromHex(levelHex) & 0xFFFFFF,
                ColorUtil.fromHex(cfg.level.bracketColorHex) & 0xFFFFFF);
    }

    private static Component buildLevel(Component base, int level, int digitStart, int digitEnd,
                                        int bracketOpen, int bracketClose, int levelRgb, int bracketRgb) {
        Component brackets = recolorRange(base, bracketOpen, bracketClose + 1, bracketRgb);
        return recolorRange(brackets, digitStart, digitEnd, levelRgb);
    }

    private static Component buildChromaLevel(Component base, int level, int digitStart, int digitEnd,
                                              int bracketOpen, int bracketClose, float speed, String bracketHex) {
        Component result = recolorRange(base, bracketOpen, bracketClose + 1, ColorUtil.fromHex(bracketHex) & 0xFFFFFF);

        for (int i = digitStart; i < digitEnd; i++) {
            result = recolorRange(result, i, i + 1, ColorUtil.chromaChar(i - digitStart, speed) & 0xFFFFFF);
        }
        return result;
    }

    public static MutableComponent recolorRange(Component base, int from, int to, int rgb) {
        MutableComponent result = Component.empty();
        int[] position = {0};
        base.visit((style, segment) -> {
            int start = position[0], end = start + segment.length();
            int a = Math.clamp(from - start, 0, segment.length());
            int b = Math.clamp(to - start, a, segment.length());
            if (a > 0) result.append(Component.literal(segment.substring(0, a)).withStyle(style));
            if (b > a) result.append(Component.literal(segment.substring(a, b)).withStyle(style.withColor(rgb)));
            if (b < segment.length()) result.append(Component.literal(segment.substring(b)).withStyle(style));
            position[0] = end;
            return Optional.empty();
        }, Style.EMPTY);
        return result;
    }

    private static Component convertLegacyBadge(Component original) {
        String raw = original.getString();
        Matcher lm = LEGACY_LEVEL_BADGE.matcher(raw);
        if (!lm.find()) return null;

        String stars    = lm.group("stars");
        int    start    = lm.start();
        int    end      = lm.end();

        Style bracketStyle = Style.EMPTY.withColor(TextColor.fromRgb(DARK_GRAY_RGB));
        Style levelStyle   = Style.EMPTY.withColor(TextColor.fromRgb(DARK_RED_RGB));

        MutableComponent badge = Component.empty();
        badge.append(Component.literal("[").withStyle(bracketStyle));
        if (stars != null && !stars.isEmpty()) {
            badge.append(Component.literal(stars).withStyle(bracketStyle));
        }
        badge.append(Component.literal(lm.group("level")).withStyle(levelStyle));
        badge.append(Component.literal("]").withStyle(bracketStyle));

        return replaceRange(original, start, end, badge);
    }

    public static Component replaceRange(Component original, int start, int end, Component replacement) {
        MutableComponent result   = Component.empty();
        boolean[]        inserted = {false};
        int[]            index    = {0};

        original.visit((style, segment) -> {
            int segStart = index[0];
            int segEnd   = segStart + segment.length();

            if (segEnd <= start || segStart >= end) {
                if (!segment.isEmpty()) {
                    result.append(Component.literal(segment).withStyle(style));
                }
            } else {
                int overlapStart = Math.max(segStart, start);
                int overlapEnd   = Math.min(segEnd, end);

                if (segStart < overlapStart) {
                    String before = segment.substring(0, overlapStart - segStart);
                    if (!before.isEmpty()) {
                        result.append(Component.literal(before).withStyle(style));
                    }
                }
                if (!inserted[0] && segStart <= start && start < segEnd) {
                    result.append(replacement);
                    inserted[0] = true;
                }
                if (overlapEnd < segEnd) {
                    String after = segment.substring(overlapEnd - segStart);
                    if (!after.isEmpty()) {
                        result.append(Component.literal(after).withStyle(style));
                    }
                }
            }
            index[0] = segEnd;
            return Optional.empty();
        }, Style.EMPTY);

        return result;
    }

    private static TextColor styleAt(Component component, int targetPos) {
        int[]         cursor = {0};
        TextColor[]   found  = {null};

        component.visit((style, segment) -> {
            int segStart = cursor[0];
            int segEnd   = segStart + segment.length();
            if (targetPos >= segStart && targetPos < segEnd) {
                found[0] = style.getColor();
                return Optional.of(Boolean.TRUE);
            }
            cursor[0] = segEnd;
            return Optional.empty();
        }, Style.EMPTY);

        return found[0];
    }

    private static boolean isDarkRed(TextColor color) {
        if (color == null) return false;
        int value = color.getValue();
        return value == DARK_RED_RGB || value == (0xFF000000 | DARK_RED_RGB);
    }

    private static String styleColorHex(Component component, int pos) {
        TextColor color = styleAt(component, pos);
        if (color == null) return "none";
        return String.format("#%06X", (0xFFFFFF & color.getValue()));
    }

    private static String truncate(String s) {
        return s.length() <= 40 ? s : s.substring(0, 40) + "...";
    }

    private static MutableComponent createChromaComponent(String text, float speed, Style baseStyle) {
        MutableComponent result = Component.empty();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            int rgb = ColorUtil.chromaChar(i, speed) & 0xFFFFFF;
            result.append(Component.literal(String.valueOf(c))
                    .withStyle(baseStyle.withColor(TextColor.fromRgb(rgb))));
        }
        return result;
    }

    private static MutableComponent createRainbowComponent(String text, Style baseStyle) {
        MutableComponent result = Component.empty();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == ' ') { result.append(Component.literal(" ").withStyle(baseStyle)); continue; }
            float hue = (float) i / Math.max(1, text.length());
            int rgb = java.awt.Color.HSBtoRGB(hue, 1.0f, 1.0f) & 0xFFFFFF;
            result.append(Component.literal(String.valueOf(c))
                    .withStyle(baseStyle.withColor(TextColor.fromRgb(rgb))));
        }
        return result;
    }

}
