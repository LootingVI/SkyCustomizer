package de.flori.skycustomizer.features;

import com.google.gson.JsonObject;
import de.flori.skycustomizer.config.SkyCustomizerConfig;
import de.flori.skycustomizer.discord.DiscordIpcClient;
import de.flori.skycustomizer.util.TextUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.PlayerTeam;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public final class DiscordRichPresence {
    private static final long SESSION_START = Instant.now().getEpochSecond();
    private static final DiscordIpcClient CLIENT = new DiscordIpcClient();
    private static long nextSnapshot, lastRevision = -1;

    private DiscordRichPresence() {}

    public static void tick() {
        var cfg = SkyCustomizerConfig.get();
        var settings = cfg.discord;
        if (!cfg.enabled || settings == null || !settings.enabled) {
            CLIENT.disable(cfg.enabled ? "Disabled; enable and save to connect" : "SkyCustomizer is globally disabled");
            return;
        }
        var minecraft = Minecraft.getInstance();
        if (minecraft.player == null && !settings.showInMenus) {
            CLIENT.disable("Waiting for a world; enable Show in menus to test here");
            return;
        }
        String id = settings.applicationId == null || settings.applicationId.isBlank()
                ? new SkyCustomizerConfig.DiscordConfig().applicationId : settings.applicationId.trim();
        if (!validId(id)) { CLIENT.disable("Invalid Application ID; use the shared SkyCustomizer ID"); return; }
        long now = System.nanoTime(), revision = SkyCustomizerConfig.runtimeRevision();
        if (now < nextSnapshot && revision == lastRevision) return;
        nextSnapshot = now + 1_000_000_000L;
        lastRevision = revision;
        CLIENT.update(id, activity(settings, variables(minecraft, cfg, settings)), settings.updateSeconds, settings.reconnectSeconds);
    }

    public static void refresh() { nextSnapshot = 0; CLIENT.reconnect(); tick(); }
    public static String status() { return CLIENT.status(); }
    public static void shutdown() { CLIENT.close(); }

    public static boolean validId(String id) {
        if (id == null || !id.matches("[1-9][0-9]{5,18}")) return false;
        try { return Long.parseLong(id) > 0; } catch (NumberFormatException ignored) { return false; }
    }

    private static JsonObject activity(SkyCustomizerConfig.DiscordConfig settings, Map<String, String> values) {
        JsonObject result = new JsonObject();
        result.addProperty("type", 0);
        put(result, "details", template(settings.details, values));
        put(result, "state", template(settings.state, values));
        JsonObject assets = new JsonObject();
        if (settings.largeImageKey != null && !settings.largeImageKey.isBlank()) {
            put(assets, "large_image", settings.largeImageKey.trim());
            put(assets, "large_text", template(settings.largeImageText, values));
        }
        if (settings.smallImageKey != null && !settings.smallImageKey.isBlank()) {
            put(assets, "small_image", settings.smallImageKey.trim());
            put(assets, "small_text", template(settings.smallImageText, values));
        }
        if (!assets.isEmpty()) result.add("assets", assets);
        if (settings.showTimestamp) {
            JsonObject timestamps = new JsonObject();
            timestamps.addProperty("start", SESSION_START);
            result.add("timestamps", timestamps);
        }
        return result;
    }

    private static void put(JsonObject target, String key, String value) {
        if (!value.isBlank()) target.addProperty(key, value);
    }

    private static Map<String, String> variables(Minecraft minecraft, SkyCustomizerConfig cfg, SkyCustomizerConfig.DiscordConfig settings) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("{player}", settings.sharePlayer ? minecraft.getUser().getName() : "");
        values.put("{area}", settings.shareArea ? area(minecraft) : "");
        values.put("{profile}", settings.shareProfile ? cfg.presets.currentPreset : "");
        var server = minecraft.getCurrentServer();
        values.put("{server}", settings.shareServer ? server == null ? minecraft.player == null ? "Main menu" : "Singleplayer" : server.ip : "");
        values.put("{game}", "Minecraft");
        return values;
    }

    private static String area(Minecraft minecraft) {
        if (minecraft.level == null) return "Main menu";
        var scoreboard = minecraft.level.getScoreboard();
        var objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (minecraft.player != null) {
            var team = scoreboard.getPlayersTeam(minecraft.player.getScoreboardName());
            var slot = team == null ? null : DisplaySlot.teamColorToSlot(team.getColor());
            if (slot != null && scoreboard.getDisplayObjective(slot) != null) objective = scoreboard.getDisplayObjective(slot);
        }
        if (objective != null) for (var entry : scoreboard.listPlayerScores(objective)) {
            if (entry.isHidden()) continue;
            String text = TextUtil.stripLegacyFormatting(PlayerTeam.formatNameForTeam(scoreboard.getPlayersTeam(entry.owner()), entry.ownerName()).getString());
            int marker = text.indexOf('⏣');
            if (marker < 0) marker = text.indexOf('ф');
            if (marker >= 0) return text.substring(marker + 1).trim();
        }
        return minecraft.getCurrentServer() == null ? "Singleplayer" : "Exploring";
    }

    private static String template(String source, Map<String, String> values) {
        String text = source == null ? "" : source;
        for (var entry : values.entrySet()) text = text.replace(entry.getKey(), entry.getValue() == null ? "" : entry.getValue());
        text = TextUtil.stripLegacyFormatting(text).replaceAll("[\\p{Cntrl}\\s]+", " ").trim();
        if (text.codePointCount(0, text.length()) > 128) text = text.substring(0, text.offsetByCodePoints(0, 128));
        return text;
    }
}
