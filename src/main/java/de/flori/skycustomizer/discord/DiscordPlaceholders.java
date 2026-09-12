package de.flori.skycustomizer.discord;

import de.flori.skycustomizer.config.SkyCustomizerConfig;
import de.flori.skycustomizer.util.TextUtil;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public final class DiscordPlaceholders {
    public enum Privacy {
        NONE, PLAYER, AREA, PROFILE, SERVER, EQUIPMENT, STATS, PERFORMANCE, POSITION;

        public boolean allowed(SkyCustomizerConfig.DiscordConfig settings) {
            return switch (this) {
                case NONE -> true;
                case PLAYER -> settings.sharePlayer;
                case AREA -> settings.shareArea;
                case PROFILE -> settings.shareProfile;
                case SERVER -> settings.shareServer;
                case EQUIPMENT -> settings.shareEquipment;
                case STATS -> settings.shareStats;
                case PERFORMANCE -> settings.sharePerformance;
                case POSITION -> settings.sharePosition;
            };
        }
    }

    public record Entry(String key, String description, String example, Privacy privacy) {
        public String token() { return "{" + key + "}"; }
    }

    public static final List<Entry> ALL = List.of(
            new Entry("player", "Minecraft username", "Player", Privacy.PLAYER),
            new Entry("custom_name", "Your local custom player name", "Nova", Privacy.PLAYER),
            new Entry("area", "Area from the original SkyBlock sidebar", "Mining District", Privacy.AREA),
            new Entry("profile", "Selected SkyCustomizer profile, not SkyBlock profile", "default", Privacy.PROFILE),
            new Entry("server", "Current server address", "mc.hypixel.net", Privacy.SERVER),
            new Entry("game", "Game name", "Minecraft", Privacy.NONE),
            new Entry("version", "Minecraft version", "26.1.2", Privacy.NONE),
            new Entry("session", "Time since this Minecraft session started", "1h 23m", Privacy.NONE),
            new Entry("item", "Main-hand display name, including local renames", "Hyperion", Privacy.EQUIPMENT),
            new Entry("item_id", "Main-hand Minecraft item ID, not SkyBlock ID", "minecraft:iron_sword", Privacy.EQUIPMENT),
            new Entry("item_count", "Main-hand stack size; 0 for an empty hand", "1", Privacy.EQUIPMENT),
            new Entry("item_durability", "Main-hand durability: remaining/max, or N/A", "240/250", Privacy.EQUIPMENT),
            new Entry("offhand", "Offhand display name, including local renames", "Aspect of the Void", Privacy.EQUIPMENT),
            new Entry("offhand_count", "Offhand stack size; 0 for an empty hand", "1", Privacy.EQUIPMENT),
            new Entry("helmet", "Equipped helmet display name", "Wither Goggles", Privacy.EQUIPMENT),
            new Entry("chestplate", "Equipped chestplate display name", "Storm's Chestplate", Privacy.EQUIPMENT),
            new Entry("leggings", "Equipped leggings display name", "Storm's Leggings", Privacy.EQUIPMENT),
            new Entry("boots", "Equipped boots display name", "Storm's Boots", Privacy.EQUIPMENT),
            new Entry("health", "Vanilla health points, not SkyBlock HP", "20", Privacy.STATS),
            new Entry("max_health", "Maximum vanilla health points", "20", Privacy.STATS),
            new Entry("absorption", "Vanilla absorption health points", "4", Privacy.STATS),
            new Entry("food", "Vanilla food level: 0 to 20", "20", Privacy.STATS),
            new Entry("xp_level", "Vanilla XP level, not SkyBlock level", "120", Privacy.STATS),
            new Entry("xp_progress", "Progress to next vanilla XP level: 0 to 100%", "65%", Privacy.STATS),
            new Entry("fps", "Client frames per second", "144", Privacy.PERFORMANCE),
            new Entry("ping", "Server latency in ms; empty when unavailable", "42", Privacy.PERFORMANCE),
            new Entry("coords", "Exact block coordinates: X, Y, Z", "120, 70, -35", Privacy.POSITION),
            new Entry("x", "Exact block X coordinate", "120", Privacy.POSITION),
            new Entry("y", "Exact block Y coordinate", "70", Privacy.POSITION),
            new Entry("z", "Exact block Z coordinate", "-35", Privacy.POSITION),
            new Entry("dimension", "Dimension ID, not SkyBlock island", "minecraft:overworld", Privacy.POSITION),
            new Entry("biome", "Biome ID at your current position", "minecraft:plains", Privacy.POSITION)
    );
    private static final Map<String, Entry> BY_KEY = new LinkedHashMap<>();
    private static final Map<String, String> SAMPLES = new LinkedHashMap<>();
    private static final Pattern TOKEN = Pattern.compile("\\{([a-z_]+)}");
    private static final Pattern WHITESPACE = Pattern.compile("[\\p{Cntrl}\\s]+");

    static { for (Entry entry : ALL) { BY_KEY.put(entry.key, entry); SAMPLES.put(entry.key, entry.example); } }

    private DiscordPlaceholders() {}

    public static String preview(String template, SkyCustomizerConfig.DiscordConfig settings) {
        return render(template, SAMPLES, settings);
    }

    public static String render(String template, Map<String, String> values, SkyCustomizerConfig.DiscordConfig settings) {
        var matcher = TOKEN.matcher(template == null ? "" : template);
        StringBuilder output = new StringBuilder();
        int offset = 0;
        while (matcher.find()) {
            output.append(template, offset, matcher.start());
            Entry entry = BY_KEY.get(matcher.group(1));
            output.append(entry == null ? matcher.group() : entry.privacy.allowed(settings)
                    ? java.util.Objects.toString(values.get(entry.key), "") : "");
            offset = matcher.end();
        }
        if (template != null) output.append(template, offset, template.length());
        String text = WHITESPACE.matcher(TextUtil.stripLegacyFormatting(output.toString())).replaceAll(" ").trim();
        return text.codePointCount(0, text.length()) <= 128 ? text : text.substring(0, text.offsetByCodePoints(0, 128));
    }
}
