package de.flori.skycustomizer.features;

import de.flori.skycustomizer.config.HudEditorScreen;
import de.flori.skycustomizer.config.SkyCustomizerConfig;
import de.flori.skycustomizer.util.ColorUtil;
import de.flori.skycustomizer.util.HudLayout;
import de.flori.skycustomizer.util.RegexCache;
import de.flori.skycustomizer.util.TextUtil;
import de.flori.skycustomizer.render.ScoreboardBackgrounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ScoreboardCustomizer {
    private static final Comparator<PlayerScoreEntry> SCORE_DISPLAY_ORDER =
            Comparator.comparingInt(PlayerScoreEntry::value).reversed()
                    .thenComparing(PlayerScoreEntry::owner, String.CASE_INSENSITIVE_ORDER);
    private static final int MAX_LINES = 15;
    private static final int SCORE_GAP = 5;
    private static final int TEXT_HEIGHT = 9;

    private ScoreboardCustomizer() {}

    public record SidebarLine(Component name, Component score, String id, Component source, boolean hidden) {
        public SidebarLine(Component name, Component score) { this(name, score, "", name, false); }
    }

    public record SidebarSnapshot(Component title, List<SidebarLine> lines, boolean showTitle, boolean hideNumbers) {
        public SidebarSnapshot {
            title = title == null ? Component.empty() : title;
            lines = List.copyOf(lines);
        }
    }

    public record SidebarSize(int width, int height) {}

    public static boolean isEnabled() {
        SkyCustomizerConfig cfg = SkyCustomizerConfig.get();
        return cfg != null && cfg.enabled && cfg.scoreboard != null && cfg.scoreboard.enabled;
    }

    public static SidebarSnapshot snapshot(SkyCustomizerConfig.ScoreboardConfig style) {
        return snapshot(style, false);
    }

    public static SidebarSnapshot snapshot(SkyCustomizerConfig.ScoreboardConfig style, boolean includeHidden) {
        Minecraft minecraft = Minecraft.getInstance();
        Objective objective = null;
        if (minecraft != null && minecraft.level != null) {
            Scoreboard scoreboard = minecraft.level.getScoreboard();
            if (minecraft.player != null) {
                PlayerTeam team = scoreboard.getPlayersTeam(minecraft.player.getScoreboardName());
                DisplaySlot slot = team == null ? null : DisplaySlot.teamColorToSlot(team.getColor());
                if (slot != null) objective = scoreboard.getDisplayObjective(slot);
            }
            if (objective == null) objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        }
        return objective == null ? sampleSnapshot(style, includeHidden) : snapshot(objective, style, includeHidden);
    }

    private static SidebarSnapshot snapshot(Objective objective, SkyCustomizerConfig.ScoreboardConfig style) {
        return snapshot(objective, style, false);
    }

    private static SidebarSnapshot snapshot(Objective objective, SkyCustomizerConfig.ScoreboardConfig style, boolean includeHidden) {
        return withTitle(objective.getDisplayName(), collectLines(objective.getScoreboard(), objective, style, includeHidden), style);
    }

    private static SidebarSnapshot sampleSnapshot(SkyCustomizerConfig.ScoreboardConfig style, boolean includeHidden) {

        List<String> sample = List.of(
                "§709/07/26 §8megaPIZZA", "",
                "§fLate Winter 12th", "§71:10am §b☽", "§7⏣ §6Mining District", "",
                "§fPurse: §641,423,465", "§fBits: §b904", "",
                "§fObjective §e↘", "§eTalk to §9Enid", "", "§ewww.hypixel.net"
        );
        List<SidebarLine> lines = new ArrayList<>();
        for (int i = 0; i < sample.size(); i++)
            addLine(lines, "sample:" + i, legacyComponent(sample.get(i)), Component.empty(), style, includeHidden);
        appendCustomLines(lines, style, includeHidden);
        return withTitle(legacyComponent("§e§lSKYBLOCK"), ordered(lines, style, includeHidden), style);
    }

    private static SidebarSnapshot withTitle(
            Component original, List<SidebarLine> lines, SkyCustomizerConfig.ScoreboardConfig style
    ) {
        boolean custom = style.customTitleEnabled && style.customTitle != null && !style.customTitle.isBlank();
        boolean showTitle = custom || !style.hideHypixelTitle;
        Component title = !showTitle ? Component.empty() : custom ? getCustomTitle(original, style) : original;
        boolean hideNumbers = style.hideScoreNumbers;
        return new SidebarSnapshot(title, lines, showTitle, hideNumbers);
    }

    private static Component getCustomTitle(Component original, SkyCustomizerConfig.ScoreboardConfig style) {
        if (!style.customTitleEnabled) return original;
        String title = style.customTitle == null ? "" : style.customTitle;
        if (style.chromaTitle) return createChromaTitle(title, 1.0f);
        int rgb = ColorUtil.fromHex(style.titleColorHex == null ? "#FFFFFF" : style.titleColorHex) & 0xFFFFFF;
        return TextUtil.parseLegacyFormatting(title, Style.EMPTY.withColor(TextColor.fromRgb(rgb)).withBold(true));
    }

    private static Component transformLine(Component original, SkyCustomizerConfig.ScoreboardConfig style) {
        if (original == null) return null;
        String raw = original.getString();
        String plain = stripLegacyFormatting(raw).trim();
        if(style.hiddenExactLines != null && style.hiddenExactLines.contains(plain)) return null;
        if (style.hiddenLineContains != null) {
            for (String hide : style.hiddenLineContains) {
                if (hide != null && !hide.isEmpty() && plain.contains(hide)) return null;
            }
        }

        Component renamed = original;
        if (style.renameRules != null && !style.renameRules.isEmpty()) {
            String text = raw;
            for (var entry : style.renameRules.entrySet()) {
                try {
                    if (entry.getKey() != null && !entry.getKey().isEmpty()) {
                        var pattern = RegexCache.compile(entry.getKey());
                        if (pattern != null) text = pattern.matcher(text).replaceAll(entry.getValue() == null ? "" : entry.getValue());
                    }
                } catch (IllegalArgumentException | IndexOutOfBoundsException ignored) {

                }
            }
            if (!text.equals(raw)) renamed = legacyComponent(text);
        }
        var cfg = SkyCustomizerConfig.get();
        if (cfg.enabled && cfg.name.enabled && cfg.name.applyInScoreboard) {
            var minecraft = Minecraft.getInstance();
            if (minecraft != null && minecraft.getUser() != null)
                renamed = NameCustomizer.customizeScoreboard(renamed, minecraft.getUser().getName());
        }
        return LevelCustomizer.processComponent(renamed);
    }

    public static SidebarSize measure(SidebarSnapshot snapshot, SkyCustomizerConfig.ScoreboardConfig style) {
        if (!snapshot.showTitle() && snapshot.lines().isEmpty()) return new SidebarSize(0, 0);
        Font font = Minecraft.getInstance().font;
        int width = snapshot.showTitle() ? font.width(snapshot.title()) : 0;
        for (SidebarLine line : snapshot.lines()) {
            int lineWidth = scaledWidth(font.width(line.name()), style.lineFontScale);
            if (hasScore(snapshot, line)) lineWidth += SCORE_GAP + scaledWidth(font.width(line.score()), style.lineFontScale);
            width = Math.max(width, lineWidth);
        }
        int padding = padding(style);
        int count = snapshot.lines().size();
        int bodyHeight = count == 0 ? 0 : lineHeight(style) * count + lineSpacing(style) * (count - 1);
        int height = padding * 2 + bodyHeight + (snapshot.showTitle() ? TEXT_HEIGHT + titleGap(snapshot, style) : 0);
        return new SidebarSize(Math.max(42, width + padding * 2), height);
    }

    public static void draw(
            GuiGraphicsExtractor graphics, SidebarSnapshot snapshot, SkyCustomizerConfig.ScoreboardConfig style,
            float left, float top, float scale
    ) {
        if (graphics == null || !Float.isFinite(scale) || scale <= 0) return;
        SidebarSize size = measure(snapshot, style);
        if (size.width() <= 0 || size.height() <= 0) return;
        Font font = Minecraft.getInstance().font;
        int padding = padding(style);
        int background = getBackgroundColor(style);
        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate(left, top);
            graphics.pose().scale(scale, scale);
            if ((background >>> 24) != 0) {
                int shadow = Math.min(96, Math.max(18, (background >>> 24) / 2)) << 24;
                fillPanel(graphics, -1, 2, size.width() + 2, size.height() + 2, shadow, style.roundedBackground);
                fillPanel(graphics, 0, 0, size.width(), size.height(), background, style.roundedBackground);
            }
            if (ScoreboardBackgrounds.draw(graphics, style, size.width(), size.height()) && style.backgroundImageShade > 0)
                fillPanel(graphics, 0, 0, size.width(), size.height(), Math.clamp(style.backgroundImageShade, 0, 255) << 24, style.roundedBackground);
            int y = padding;
            if (snapshot.showTitle()) {
                graphics.text(font, snapshot.title(), (size.width() - font.width(snapshot.title())) / 2,
                        y, -1, style.titleShadow);
                y += TEXT_HEIGHT;
                int gap = titleGap(snapshot, style);
                if (gap > 0 && !style.compactStyle) {
                    int alpha = Math.min(70, Math.max(16, (background >>> 24) + 8));
                    graphics.fill(padding, y, size.width() - padding, y + 1, (alpha << 24) | 0xFFFFFF);
                }
                y += gap;
            }
            for (SidebarLine line : snapshot.lines()) {
                int lineColor = lineColor(style, line.id());
                drawScaledText(graphics, font, line.name(), padding, y, style.lineFontScale, lineColor);
                if (hasScore(snapshot, line)) {
                    int scoreWidth = scaledWidth(font.width(line.score()), style.lineFontScale);
                    drawScaledText(graphics, font, line.score(), size.width() - padding - scoreWidth, y,
                            style.lineFontScale, scoreColor(style, line.id()));
                }
                y += lineHeight(style) + lineSpacing(style);
            }
        } finally {
            graphics.pose().popMatrix();
        }
    }

    public static boolean renderCompactSidebar(GuiGraphicsExtractor graphics, Objective objective) {
        if (Minecraft.getInstance().screen instanceof HudEditorScreen) return true;
        if (!isEnabled() || graphics == null || objective == null) return false;
        try {
            SkyCustomizerConfig.ScoreboardConfig style = SkyCustomizerConfig.get().scoreboard;
            SidebarSnapshot snapshot = snapshot(objective, style);
            SidebarSize size = measure(snapshot, style);
            HudLayout.Bounds bounds = HudLayout.scoreboard(graphics.guiWidth(), graphics.guiHeight(),
                    size.width(), size.height(), style.scale, style.offsetX, style.offsetY);
            draw(graphics, snapshot, style, bounds.x(), bounds.y(), bounds.scale());
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static List<SidebarLine> collectLines(
            Scoreboard scoreboard, Objective objective, SkyCustomizerConfig.ScoreboardConfig style, boolean includeHidden
    ) {
        List<PlayerScoreEntry> entries = new ArrayList<>(scoreboard.listPlayerScores(objective));
        entries.removeIf(PlayerScoreEntry::isHidden);
        entries.sort(SCORE_DISPLAY_ORDER);
        List<SidebarLine> lines = new ArrayList<>();
        for (int i = 0; i < entries.size() && i < 100; i++) {
            if (!includeHidden && style.lineOrder.isEmpty() && lines.size() >= MAX_LINES) break;
            PlayerScoreEntry entry = entries.get(i);
            Component serverName = PlayerTeam.formatNameForTeam(scoreboard.getPlayersTeam(entry.owner()), entry.ownerName());
            Component score = entry.formatValue(objective.numberFormatOrDefault(StyledFormat.SIDEBAR_DEFAULT));
            addLine(lines, "server:" + entry.owner(), serverName, score, style, includeHidden);
        }
        appendCustomLines(lines, style, includeHidden);
        return ordered(lines, style, includeHidden);
    }

    private static void appendCustomLines(List<SidebarLine> lines, SkyCustomizerConfig.ScoreboardConfig style, boolean includeHidden) {
        if (style.customLines == null) return;
        for (int i = 0; i < style.customLines.size() && i < 100; i++) {
            String custom = style.customLines.get(i);
            if (custom == null) continue;
            String id = i < style.customLineKeys.size() ? style.customLineKeys.get(i) : "legacy:" + i;
            addLine(lines, id, legacyComponent(custom), null, style, includeHidden);
        }
    }

    private static void addLine(List<SidebarLine> lines, String id, Component source, Component score,
                                SkyCustomizerConfig.ScoreboardConfig style, boolean includeHidden) {
        Component name = transformLine(source, style);
        boolean hidden = name == null || style.hiddenLineIds.contains(id);
        if (hidden && !includeHidden) return;
        if (style.lineOverrides.containsKey(id)) name = legacyComponent(style.lineOverrides.get(id));
        if (name != null && style.lineIcons != null && !style.lineIcons.isEmpty()) {
            String plain = name.getString();
            for (var entry : style.lineIcons.entrySet()) {
                if (entry.getKey() != null && !entry.getKey().isBlank() && entry.getValue() != null
                        && !entry.getValue().isBlank() && plain.toLowerCase(java.util.Locale.ROOT)
                        .contains(entry.getKey().toLowerCase(java.util.Locale.ROOT))) {
                    name = Component.literal(entry.getValue() + " ").append(name);
                    break;
                }
            }
        }
        lines.add(new SidebarLine(name == null ? source : name, score, id, source, hidden));
    }

    private static List<SidebarLine> ordered(List<SidebarLine> lines, SkyCustomizerConfig.ScoreboardConfig style, boolean includeHidden) {
        if (!style.lineOrder.isEmpty()) {
            var ranks = new java.util.HashMap<String, Integer>();
            for (int i = 0; i < style.lineOrder.size(); i++) ranks.putIfAbsent(style.lineOrder.get(i), i);
            lines.sort(Comparator.comparingInt(line -> ranks.getOrDefault(line.id(), Integer.MAX_VALUE)));
        }
        return lines.subList(0, Math.min(lines.size(), includeHidden ? 100 : MAX_LINES));
    }

    private static boolean hasScore(SidebarSnapshot snapshot, SidebarLine line) {
        return !snapshot.hideNumbers() && line.score() != null && !line.score().getString().isEmpty();
    }

    private static int padding(SkyCustomizerConfig.ScoreboardConfig style) {
        return clamp(style.padding, 2, 14);
    }

    private static int lineSpacing(SkyCustomizerConfig.ScoreboardConfig style) {
        return clamp(style.lineSpacing, 0, 5);
    }

    public static int lineHeight(SkyCustomizerConfig.ScoreboardConfig style) {
        return Math.max(6, scaledWidth(TEXT_HEIGHT, style.lineFontScale));
    }

    private static int scaledWidth(int width, float scale) {
        float safeScale = Float.isFinite(scale) ? Math.clamp(scale, 0.6f, 2.0f) : 1.0f;
        return Math.max(1, Math.round(width * safeScale));
    }

    private static void drawScaledText(GuiGraphicsExtractor graphics, Font font, Component text,
                                       int x, int y, float scale, int color) {
        float safeScale = Float.isFinite(scale) ? Math.clamp(scale, 0.6f, 2.0f) : 1.0f;
        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate(x, y);
            graphics.pose().scale(safeScale, safeScale);
            graphics.text(font, text, 0, 0, color, false);
        } finally {
            graphics.pose().popMatrix();
        }
    }

    private static int lineColor(SkyCustomizerConfig.ScoreboardConfig style, String id) {
        return configuredColor(style.lineColorHex, style, id);
    }

    private static int scoreColor(SkyCustomizerConfig.ScoreboardConfig style, String id) {
        return configuredColor(style.scoreColorHex, style, id);
    }

    private static int configuredColor(String hex, SkyCustomizerConfig.ScoreboardConfig style, String id) {
        if (hex == null || hex.isBlank()) return -1;
        int rgb = ColorUtil.fromHex(hex) & 0xFFFFFF;
        if (!style.lineAnimation) return 0xFF000000 | rgb;
        double wave = Math.sin(System.nanoTime() / 1_000_000_000.0 * Math.clamp(style.lineAnimationSpeed, 0.1f, 4.0f)
                * Math.PI * 2 + Math.floorMod(id.hashCode(), 13));
        float factor = (float) (0.72 + (wave + 1) * 0.14);
        int r = Math.clamp(Math.round(((rgb >>> 16) & 255) * factor), 0, 255);
        int g = Math.clamp(Math.round(((rgb >>> 8) & 255) * factor), 0, 255);
        int b = Math.clamp(Math.round((rgb & 255) * factor), 0, 255);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int titleGap(SidebarSnapshot snapshot, SkyCustomizerConfig.ScoreboardConfig style) {
        return snapshot.showTitle() && !snapshot.lines().isEmpty() ? (style.compactStyle ? 2 : 4) : 0;
    }

    private static void fillPanel(
            GuiGraphicsExtractor graphics, int left, int top, int right, int bottom, int color, boolean rounded
    ) {
        if (!rounded || right - left < 10 || bottom - top < 10) {
            graphics.fill(left, top, right, bottom, color);
            return;
        }

        graphics.fill(left + 3, top, right - 3, top + 1, color);
        graphics.fill(left + 1, top + 1, right - 1, top + 3, color);
        graphics.fill(left, top + 3, right, bottom - 3, color);
        graphics.fill(left + 1, bottom - 3, right - 1, bottom - 1, color);
        graphics.fill(left + 3, bottom - 1, right - 3, bottom, color);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int getBackgroundColor(SkyCustomizerConfig.ScoreboardConfig style) {
        if (!style.backgroundEnabled) return 0;
        int rgb = ColorUtil.fromHex(style.backgroundColorHex == null ? "#000000" : style.backgroundColorHex) & 0xFFFFFF;
        return (clamp(style.backgroundOpacity, 0, 255) << 24) | rgb;
    }

    private static Component createChromaTitle(String text, float speed) {
        MutableComponent out = Component.empty();
        String clean = stripLegacyFormatting(text);
        for (int i = 0; i < clean.length(); i++) {
            int rgb = ColorUtil.chromaChar(i, speed) & 0xFFFFFF;
            out.append(Component.literal(String.valueOf(clean.charAt(i)))
                    .withStyle(s -> s.withColor(TextColor.fromRgb(rgb)).withBold(true)));
        }
        return out;
    }

    private static MutableComponent legacyComponent(String text) {
        return TextUtil.parseLegacyFormatting(text, Style.EMPTY);
    }

    private static String stripLegacyFormatting(String text) {
        return TextUtil.stripLegacyFormatting(text);
    }
}
