package de.flori.skycustomizer.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import de.flori.skycustomizer.util.ColorUtil;
import de.flori.skycustomizer.util.LevelColorUtil;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class SkyCustomizerConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static SkyCustomizerConfig INSTANCE;
    private static volatile long runtimeRevision;

    public static long runtimeRevision() { return runtimeRevision; }

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("skycustomizer.json");
    }

    public boolean enabled = true;
    public String configVersion = "1.0.0";
    public UiConfig ui = new UiConfig();
    public static class UiConfig {
        public boolean smoothText = true;
        public boolean animations = true;
        public boolean backgroundBlur = false;
        public Set<String> favorites = new LinkedHashSet<>();
        public List<String> favoriteColors = new ArrayList<>(List.of("#E2944E", "#55FFFF", "#FF55FF"));
        public List<String> recentColors = new ArrayList<>();
        public Map<String, String> gradients = new LinkedHashMap<>();
    }

    public NameConfig name = new NameConfig();
    public static class NameConfig {
        public boolean enabled = true;
        public boolean useCustomName = false;
        public String customName = "";
        public String colorHex = "#55FFFF";
        public boolean bold = false;
        public boolean italic = false;
        public boolean underline = false;
        public boolean strikethrough = false;
        public boolean obfuscated = false;
        public String prefix = "";
        public String suffix = "";
        public boolean chroma = false;
        public float chromaSpeed = 1.0f;
        public boolean rainbow = false;

        public boolean applyInChat = true;
        public boolean applyInTab = true;
        public boolean applyOverHead = true;
        public boolean applyInScoreboard = false;
        public boolean hidePlayerNametags = false;
        public boolean hideNametagScores = false;
        public float nametagMaxDistance = 64.0f;
        public float nametagOffsetY = 0.0f;
        public boolean recolorOtherPlayers = false;
        public String otherPlayerColorHex = "#FFFFFF";
        public boolean nametagPill = false;
        public String nametagPillColorHex = "#3B281B";
        public boolean nametagHeadIcon = false;
        public String nametagHeadSymbol = "●";
        public int nametagSpacing = 1;
        public boolean nametagDistanceFade = false;
        public float nametagFadeStart = 32.0f;
        public String friendPrefix = "✦ ";
        public String friendColorHex = "#55FFFF";
        public String guildPrefix = "◆ ";
        public String guildColorHex = "#55FF55";
        public Map<String, String> playerAliases = new LinkedHashMap<>();
        public Map<String, String> playerBadges = new LinkedHashMap<>();
        public Map<String, String> rankIcons = new LinkedHashMap<>();
        public List<String> friendNames = new ArrayList<>();
        public List<String> guildNames = new ArrayList<>();
        public List<NametagRule> rules = new ArrayList<>();
    }
    public static class NametagRule {
        public boolean enabled = true;
        public String target = "ANY";
        public String username = "";
        public int minLevel = 0, maxLevel = 10000;
        public String colorHex = "#FFFFFF", prefix = "", suffix = "";
        public boolean bold, italic, hidden;
    }

    public LevelConfig level = new LevelConfig();
    public static class LevelConfig {
        public boolean enabled = true;
        public boolean overrideHypixelColors = true;
        public boolean chromaFor600Plus = true;
        public float chromaSpeed = 1.0f;
        public boolean gradientBetweenLevels = true;

        public Map<Integer, String> customColors = new TreeMap<>();

        public String bracketColorHex = "#555555";

        public LevelConfig() {
            customColors.putAll(LevelColorUtil.getDefaultExtendedHexMap());
        }

        public String getColorForLevel(int lvl) {
            if (customColors == null || customColors.isEmpty()) return "#AAAAAA";

            if (!(customColors instanceof NavigableMap)) customColors = new TreeMap<>(customColors);
            NavigableMap<Integer, String> thresholds = (NavigableMap<Integer, String>) customColors;
            Map.Entry<Integer, String> lower = thresholds.floorEntry(lvl);
            Map.Entry<Integer, String> upper = thresholds.ceilingEntry(lvl);

            if (lower == null) return thresholds.firstEntry().getValue();
            if (!gradientBetweenLevels || upper == null || lower.getKey().equals(upper.getKey())) {
                return lower.getValue();
            }

            float progress = (float) (lvl - lower.getKey()) / (upper.getKey() - lower.getKey());
            return ColorUtil.toHex(ColorUtil.lerp(lower.getValue(), upper.getValue(), progress));
        }
    }

    public ScoreboardConfig scoreboard = new ScoreboardConfig();
    public static class ScoreboardConfig {
        public boolean enabled = true;
        public boolean customTitleEnabled = true;
        public String customTitle = "§a§lSKYBLOCK §7(custom)";
        public String titleColorHex = "#55FF55";
        public boolean hideHypixelTitle = false;
        public List<String> hiddenLineContains = new ArrayList<>(List.of());
        public List<String> hiddenExactLines = new ArrayList<>();
        public Map<String, String> renameRules = new LinkedHashMap<>();
        public List<String> customLines = new ArrayList<>();
        public List<String> customLineKeys = new ArrayList<>();
        public Map<String, String> lineOverrides = new LinkedHashMap<>();
        public List<String> hiddenLineIds = new ArrayList<>();
        public List<String> lineOrder = new ArrayList<>();
        public boolean backgroundEnabled = true;
        public String backgroundColorHex = "#000000";
        public int backgroundOpacity = 80;
        public boolean backgroundImageEnabled = false;
        public String backgroundImage = "";
        public String backgroundImageFit = "COVER";
        public int backgroundImageOpacity = 255;
        public int backgroundImageShade = 60;
        public float scale = 1.0f;
        public int offsetX = 0;
        public int offsetY = 0;
        public boolean hideScoreNumbers = false;
        public boolean chromaTitle = false;

        public boolean compactStyle = true;
        public boolean roundedBackground = true;
        public int padding = 5;
        public int lineSpacing = 1;
        public boolean titleShadow = true;
        public float lineFontScale = 1.0f;
        public String lineColorHex = "";
        public String scoreColorHex = "";
        public boolean lineAnimation = false;
        public float lineAnimationSpeed = 1.0f;
        public Map<String, String> lineIcons = new LinkedHashMap<>();
    }

    public ItemConfig items = new ItemConfig();
    public static class ItemConfig {
        public boolean enabled = true;
        public boolean namesEnabled = true;
        public boolean loreEnabled = true;
        public boolean rarityColorsEnabled = false;
        public boolean showOriginalName = false;
        public boolean hideLore = false;
        public boolean hideGlint = false;
        public boolean forceGlint = false;
        public boolean itemIconsEnabled = false;
        public String itemIcon = "◆";
        public boolean favoriteMarkerEnabled = true;
        public String favoriteMarker = "★";
        public boolean tooltipStyleEnabled = false;
        public String tooltipAccentHex = "#FFAA00";

        public Map<String, String> heldItemNames = new LinkedHashMap<>();
        public Map<String, String> heldItemLore = new LinkedHashMap<>();
        public Map<String, String> heldItemLabels = new LinkedHashMap<>();

        public Map<String, String> rarityColors = new LinkedHashMap<>();

        public Map<String, String> customNames = new LinkedHashMap<>();
        public Map<String, String> customLore = new LinkedHashMap<>();
        public Map<String, String> itemTypeNames = new LinkedHashMap<>();
        public List<String> favoriteItemTypes = new ArrayList<>();

        public List<RegexRule> regexRules = new ArrayList<>();

        public ItemConfig() {
            rarityColors.put("COMMON", "#FFFFFF");
            rarityColors.put("UNCOMMON", "#55FF55");
            rarityColors.put("RARE", "#5555FF");
            rarityColors.put("EPIC", "#AA00AA");
            rarityColors.put("LEGENDARY", "#FFAA00");
            rarityColors.put("MYTHIC", "#FF55FF");
            rarityColors.put("DIVINE", "#55FFFF");
            rarityColors.put("SPECIAL", "#FF5555");
            rarityColors.put("VERY_SPECIAL", "#FF5555");

        }

        public static class RegexRule {
            public String pattern = "";
            public String replacement = "";
            public boolean enabled = true;
            public RegexRule() {}
            public RegexRule(String p, String r) { pattern = p; replacement = r; }
        }
    }

    public ArmorConfig armor = new ArmorConfig();
    public static class ArmorConfig {
        public boolean enabled = true;
        public boolean perPiece = true;
        public String helmetHex = "#FF5555";
        public String chestplateHex = "#55FF55";
        public String leggingsHex = "#5555FF";
        public String bootsHex = "#FFAA00";
        public String globalHex = "#FF55FF";
        public boolean chroma = false;
        public float chromaSpeed = 1.0f;
    }

    public ChatConfig chat = new ChatConfig();
    public static class ChatConfig {
        public boolean enabled = true;
        public boolean levelColorInChat = true;
        public boolean recolorChatTypes = true;
        public Map<String, String> chatTypeColors = new LinkedHashMap<>();
        public List<String> hideMessageContains = new ArrayList<>();
        public List<String> hideMessageRegex = new ArrayList<>();
        public Map<String, String> highlightWords = new LinkedHashMap<>();
        public boolean timestampEnabled = false;
        public String timestampColorHex = "#AAAAAA";
        public String timestampFormat = "HH:mm:ss";
        public boolean compactChat = false;
        public String chatTheme = "CLASSIC";
        public String chatAccentHex = "#E2944E";
        public boolean chatAccentBar = false;
        public boolean mentionSound = true;
        public boolean keywordSound = false;
        public boolean duplicateGrouping = true;

        public ChatConfig() {
            chatTypeColors.put("GUILD", "#55FF55");
            chatTypeColors.put("PARTY", "#5555FF");
            chatTypeColors.put("COOP", "#FFAA00");
            chatTypeColors.put("PRIVATE", "#FF55FF");
        }
    }

    public VisualsConfig visuals = new VisualsConfig();
    public static class VisualsConfig {
        public boolean enabled = true;
        public boolean customHud = true;
        public float hudScale = 1.0f;
        public int hudOffsetX = 0;
        public int hudOffsetY = 0;
        public boolean customTitles = true;
        public boolean hideTitles = false;
        public String titleHex = "#FFAA00";
        public String subtitleHex = "#55FFFF";
        public boolean titleChroma = false;
        public boolean bossbarCustom = true;
        public boolean bossbarHide = false;
        public boolean actionbarCustom = true;
        public boolean actionbarHide = false;
        public boolean vignetteHide = true;
        public boolean performanceHud = false;
        public boolean performanceShowFps = true;
        public boolean performanceShowPing = true;
        public boolean performanceShowTps = true;
        public float performanceHudScale = 1.0f;
        public int performanceHudOffsetX = 0;
        public int performanceHudOffsetY = 0;
        public boolean performanceHudBackground = true;
        public int performanceHudBackgroundOpacity = 110;
        public String performanceHudColorHex = "#FFFFFF";
    }

    public PresetConfig presets = new PresetConfig();
    public static class PresetConfig {
        public String currentPreset = "default";
        public List<String> savedPresets = new ArrayList<>(List.of("default"));
    }

    public boolean debugMode = false;

    public static SkyCustomizerConfig get() {
        if (INSTANCE == null) {
            INSTANCE = load();
        }
        return INSTANCE;
    }

    public static SkyCustomizerConfig load() {
        Path configPath = configPath();
        System.out.println("[SkyCustomizer] Loading config from: " + configPath);
        if (Files.exists(configPath)) {
            try {
                String json = Files.readString(configPath);
                SkyCustomizerConfig cfg = decode(json);
                if (cfg != null) {
                    if (INSTANCE != null) {

                        copyInto(INSTANCE, cfg);
                        System.out.println("[SkyCustomizer] Config reloaded successfully (in place)");
                        return INSTANCE;
                    }
                    INSTANCE = cfg;
                    System.out.println("[SkyCustomizer] Config loaded successfully");
                    return cfg;
                }
            } catch (Exception e) {
                System.err.println("[SkyCustomizer] Failed to load config: " + e.getMessage());
                e.printStackTrace();
            }

            System.err.println("[SkyCustomizer] Keeping unreadable config file; using in-memory defaults.");
            SkyCustomizerConfig fallback = new SkyCustomizerConfig();
            if (INSTANCE != null) {
                copyInto(INSTANCE, fallback);
                return INSTANCE;
            }
            INSTANCE = fallback;
            return fallback;
        }
        System.out.println("[SkyCustomizer] Creating default config");
        SkyCustomizerConfig def = new SkyCustomizerConfig();
        def.save();
        INSTANCE = def;
        return def;
    }

    public void save() {
        runtimeRevision++;
        try {
            Path configPath = configPath();
            System.out.println("[SkyCustomizer] Saving config to: " + configPath);
            Files.createDirectories(configPath.getParent());
            String json = GSON.toJson(this);
            Files.writeString(configPath, json);
            System.out.println("[SkyCustomizer] Config saved successfully");
        } catch (IOException e) {
            System.err.println("[SkyCustomizer] Failed to save config: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public Path getPresetPath(String name) {
        if(name==null || !name.matches("[A-Za-z0-9 _-]{1,48}")) throw new IllegalArgumentException("Use 1-48 letters, numbers, spaces, - or _ for the profile name.");
        return FabricLoader.getInstance().getConfigDir().resolve("skycustomizer/presets/" + name + ".json");
    }

    public SkyCustomizerConfig previewPreset(String name) throws IOException {
        SkyCustomizerConfig preview=decode(Files.readString(getPresetPath(name)));
        if(preview==null)throw new IOException("Empty profile");
        return preview;
    }

    public void savePreset(String name) {
        try {
            Path p = getPresetPath(name);
            Files.createDirectories(p.getParent());
            Files.writeString(p, GSON.toJson(this));
            if (!presets.savedPresets.contains(name)) presets.savedPresets.add(name);
            save();
        } catch (IOException e) { e.printStackTrace(); }
    }

    public boolean loadPreset(String name) {
        Path p = getPresetPath(name);
        if (!Files.exists(p)) return false;
        try {
            String json = Files.readString(p);
            SkyCustomizerConfig loaded = decode(json);
            if (loaded != null) {
                List<String> knownPresets = new ArrayList<>(get().presets.savedPresets);

                copyInto(get(), loaded);
                get().presets.savedPresets = knownPresets;
                if (!get().presets.savedPresets.contains(name)) get().presets.savedPresets.add(name);
                get().presets.currentPreset = name;
                get().save();
                return true;
            }
        } catch (Exception e) { e.printStackTrace(); }
        return false;
    }

    private static void copyInto(SkyCustomizerConfig dst, SkyCustomizerConfig src) {
        runtimeRevision++;
        dst.enabled = src.enabled;
        dst.configVersion = src.configVersion;
        dst.ui = src.ui != null ? src.ui : new UiConfig();
        dst.name = src.name != null ? src.name : new NameConfig();
        dst.level = src.level != null ? src.level : new LevelConfig();
        dst.scoreboard = src.scoreboard != null ? src.scoreboard : new ScoreboardConfig();
        dst.items = src.items != null ? src.items : new ItemConfig();
        dst.armor = src.armor != null ? src.armor : new ArmorConfig();
        dst.chat = src.chat != null ? src.chat : new ChatConfig();
        dst.visuals = src.visuals != null ? src.visuals : new VisualsConfig();
        dst.presets = src.presets != null ? src.presets : new PresetConfig();
        dst.debugMode = src.debugMode;
    }

    static SkyCustomizerConfig decode(String json) {
        var document = JsonParser.parseString(json);
        SkyCustomizerConfig config = GSON.fromJson(document, SkyCustomizerConfig.class);
        if (config == null) return null;
        normalize(config);
        var legacyNumbers = document.getAsJsonObject().get("hideScoreboardNumbers");
        if (legacyNumbers != null && legacyNumbers.isJsonPrimitive()
                && legacyNumbers.getAsJsonPrimitive().isBoolean() && legacyNumbers.getAsBoolean()) {
            config.scoreboard.hideScoreNumbers = true;
        }
        return config;
    }

    private static void normalize(SkyCustomizerConfig cfg) {
        runtimeRevision++;
        if (cfg.ui == null) cfg.ui = new UiConfig();
        if (cfg.ui.favorites == null) cfg.ui.favorites = new LinkedHashSet<>();
        if (cfg.ui.favoriteColors == null) cfg.ui.favoriteColors = new ArrayList<>();
        if (cfg.ui.recentColors == null) cfg.ui.recentColors = new ArrayList<>();
        if (cfg.ui.gradients == null) cfg.ui.gradients = new LinkedHashMap<>();
        if (cfg.level == null) cfg.level = new LevelConfig();
        if (cfg.level.customColors == null || cfg.level.customColors.isEmpty()) {
            cfg.level.customColors = new TreeMap<>(LevelColorUtil.getDefaultExtendedHexMap());
        }
        if (cfg.name == null) cfg.name = new NameConfig();
        if (cfg.name.rules == null) cfg.name.rules = new ArrayList<>();
        if (cfg.name.playerAliases == null) cfg.name.playerAliases = new LinkedHashMap<>();
        if (cfg.name.playerBadges == null) cfg.name.playerBadges = new LinkedHashMap<>();
        if (cfg.name.rankIcons == null) cfg.name.rankIcons = new LinkedHashMap<>();
        if (cfg.name.friendNames == null) cfg.name.friendNames = new ArrayList<>();
        if (cfg.name.guildNames == null) cfg.name.guildNames = new ArrayList<>();
        cfg.name.nametagSpacing = Math.clamp(cfg.name.nametagSpacing, 0, 4);
        cfg.name.nametagFadeStart = Math.clamp(cfg.name.nametagFadeStart, 8, 64);
        if (cfg.armor == null) cfg.armor = new ArmorConfig();
        if (cfg.chat == null) cfg.chat = new ChatConfig();
        if (cfg.chat.chatTypeColors == null) cfg.chat.chatTypeColors = new LinkedHashMap<>();
        if (cfg.chat.hideMessageContains == null) cfg.chat.hideMessageContains = new ArrayList<>();
        if (cfg.chat.hideMessageRegex == null) cfg.chat.hideMessageRegex = new ArrayList<>();
        if (cfg.chat.highlightWords == null) cfg.chat.highlightWords = new LinkedHashMap<>();
        if (cfg.scoreboard == null) cfg.scoreboard = new ScoreboardConfig();
        if (cfg.scoreboard.hiddenLineContains == null) cfg.scoreboard.hiddenLineContains = new ArrayList<>();
        if (cfg.scoreboard.hiddenExactLines == null) cfg.scoreboard.hiddenExactLines = new ArrayList<>();
        if (cfg.scoreboard.renameRules == null) cfg.scoreboard.renameRules = new LinkedHashMap<>();
        if (cfg.scoreboard.customLines == null) cfg.scoreboard.customLines = new ArrayList<>();
        if (cfg.scoreboard.customLineKeys == null) cfg.scoreboard.customLineKeys = new ArrayList<>();
        if (cfg.scoreboard.lineOverrides == null) cfg.scoreboard.lineOverrides = new LinkedHashMap<>();
        if (cfg.scoreboard.hiddenLineIds == null) cfg.scoreboard.hiddenLineIds = new ArrayList<>();
        if (cfg.scoreboard.lineOrder == null) cfg.scoreboard.lineOrder = new ArrayList<>();
        if (cfg.scoreboard.backgroundImage == null) cfg.scoreboard.backgroundImage = "";
        if (cfg.scoreboard.lineIcons == null) cfg.scoreboard.lineIcons = new LinkedHashMap<>();
        cfg.scoreboard.lineFontScale = Math.clamp(cfg.scoreboard.lineFontScale, 0.6f, 2.0f);
        cfg.scoreboard.lineAnimationSpeed = Math.clamp(cfg.scoreboard.lineAnimationSpeed, 0.1f, 4.0f);
        if (cfg.scoreboard.backgroundImageFit == null || !List.of("COVER", "CONTAIN", "STRETCH").contains(cfg.scoreboard.backgroundImageFit)) cfg.scoreboard.backgroundImageFit = "COVER";
        cfg.scoreboard.backgroundImageOpacity = Math.clamp(cfg.scoreboard.backgroundImageOpacity, 0, 255);
        cfg.scoreboard.backgroundImageShade = Math.clamp(cfg.scoreboard.backgroundImageShade, 0, 255);
        de.flori.skycustomizer.util.ScoreboardLines.ensureKeys(cfg.scoreboard);
        if (cfg.items == null) cfg.items = new ItemConfig();
        if (cfg.items.heldItemNames == null) cfg.items.heldItemNames = new LinkedHashMap<>();
        if (cfg.items.heldItemLore == null) cfg.items.heldItemLore = new LinkedHashMap<>();
        if (cfg.items.heldItemLabels == null) cfg.items.heldItemLabels = new LinkedHashMap<>();
        if (cfg.items.rarityColors == null) cfg.items.rarityColors = new LinkedHashMap<>();
        if (cfg.items.customNames == null) cfg.items.customNames = new LinkedHashMap<>();
        if (cfg.items.customLore == null) cfg.items.customLore = new LinkedHashMap<>();
        if (cfg.items.itemTypeNames == null) cfg.items.itemTypeNames = new LinkedHashMap<>();
        if (cfg.items.favoriteItemTypes == null) cfg.items.favoriteItemTypes = new ArrayList<>();
        if (cfg.items.regexRules == null) cfg.items.regexRules = new ArrayList<>();
        if (cfg.visuals == null) cfg.visuals = new VisualsConfig();
        cfg.visuals.performanceHudScale = Math.clamp(cfg.visuals.performanceHudScale, 0.5f, 3.0f);
        cfg.visuals.performanceHudBackgroundOpacity = Math.clamp(cfg.visuals.performanceHudBackgroundOpacity, 0, 255);
        if (cfg.presets == null) cfg.presets = new PresetConfig();
        if (cfg.presets.savedPresets == null) cfg.presets.savedPresets = new ArrayList<>();
    }
}
