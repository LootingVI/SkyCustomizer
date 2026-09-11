package de.flori.skycustomizer.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;

public final class ScoreboardStyles {

    private ScoreboardStyles() {}

    public static SkyCustomizerConfig.ScoreboardConfig copy(SkyCustomizerConfig.ScoreboardConfig source) {
        SkyCustomizerConfig.ScoreboardConfig target = new SkyCustomizerConfig.ScoreboardConfig();
        if (source == null) return target;

        target.enabled = source.enabled;
        target.customTitleEnabled = source.customTitleEnabled;
        target.customTitle = source.customTitle;
        target.titleColorHex = source.titleColorHex;
        target.hideHypixelTitle = source.hideHypixelTitle;
        target.hiddenLineContains = source.hiddenLineContains == null
                ? new ArrayList<>() : new ArrayList<>(source.hiddenLineContains);
        target.hiddenExactLines = source.hiddenExactLines == null ? new ArrayList<>() : new ArrayList<>(source.hiddenExactLines);
        target.renameRules = source.renameRules == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(source.renameRules);
        target.customLines = source.customLines == null
                ? new ArrayList<>() : new ArrayList<>(source.customLines);
        target.customLineKeys = source.customLineKeys == null ? new ArrayList<>() : new ArrayList<>(source.customLineKeys);
        target.lineOverrides = source.lineOverrides == null ? new LinkedHashMap<>() : new LinkedHashMap<>(source.lineOverrides);
        target.hiddenLineIds = source.hiddenLineIds == null ? new ArrayList<>() : new ArrayList<>(source.hiddenLineIds);
        target.lineOrder = source.lineOrder == null ? new ArrayList<>() : new ArrayList<>(source.lineOrder);
        target.backgroundEnabled = source.backgroundEnabled;
        target.backgroundColorHex = source.backgroundColorHex;
        target.backgroundOpacity = source.backgroundOpacity;
        target.backgroundImageEnabled = source.backgroundImageEnabled;
        target.backgroundImage = source.backgroundImage;
        target.backgroundImageFit = source.backgroundImageFit;
        target.backgroundImageOpacity = source.backgroundImageOpacity;
        target.backgroundImageShade = source.backgroundImageShade;
        target.scale = source.scale;
        target.offsetX = source.offsetX;
        target.offsetY = source.offsetY;
        target.hideScoreNumbers = source.hideScoreNumbers;
        target.chromaTitle = source.chromaTitle;
        target.compactStyle = source.compactStyle;
        target.roundedBackground = source.roundedBackground;
        target.padding = source.padding;
        target.lineSpacing = source.lineSpacing;
        target.titleShadow = source.titleShadow;
        return target;
    }

    public static void applyScreenshot(SkyCustomizerConfig.ScoreboardConfig style) {
        if (style == null) return;
        style.customTitleEnabled = true;
        style.customTitle = "SKYBLOCK";
        style.titleColorHex = "#FFFF55";
        style.hideHypixelTitle = false;
        style.backgroundEnabled = true;
        style.backgroundColorHex = "#000000";
        style.backgroundOpacity = 100;
        style.hideScoreNumbers = true;
        style.chromaTitle = false;
        style.compactStyle = true;
        style.roundedBackground = true;
        style.padding = 5;
        style.lineSpacing = 0;
        style.titleShadow = true;
    }

    public static void applyMinimal(SkyCustomizerConfig.ScoreboardConfig style) {
        if (style == null) return;
        style.customTitleEnabled = false;
        style.hideHypixelTitle = false;
        style.backgroundEnabled = false;
        style.backgroundColorHex = "#000000";
        style.backgroundOpacity = 0;
        style.hideScoreNumbers = true;
        style.chromaTitle = false;
        style.compactStyle = true;
        style.roundedBackground = false;
        style.padding = 3;
        style.lineSpacing = 0;
        style.titleShadow = false;
    }

    public static void applyDark(SkyCustomizerConfig.ScoreboardConfig style) {
        if (style == null) return;
        style.customTitleEnabled = true;
        style.customTitle = "SKYBLOCK";
        style.titleColorHex = "#55FF55";
        style.hideHypixelTitle = false;
        style.backgroundEnabled = true;
        style.backgroundColorHex = "#0E0C12";
        style.backgroundOpacity = 215;
        style.hideScoreNumbers = false;
        style.chromaTitle = false;
        style.compactStyle = true;
        style.roundedBackground = true;
        style.padding = 6;
        style.lineSpacing = 1;
        style.titleShadow = true;
    }
}
