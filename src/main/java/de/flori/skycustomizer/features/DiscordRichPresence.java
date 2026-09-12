package de.flori.skycustomizer.features;

import com.google.gson.JsonObject;
import de.flori.skycustomizer.config.SkyCustomizerConfig;
import de.flori.skycustomizer.discord.DiscordIpcClient;
import de.flori.skycustomizer.discord.DiscordPlaceholders;
import de.flori.skycustomizer.util.TextUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.PlayerTeam;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public final class DiscordRichPresence {
    private static final long SESSION_START = Instant.now().getEpochSecond();
    private static final long SESSION_NANOS = System.nanoTime();
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
        put(result, "details", DiscordPlaceholders.render(settings.details, values, settings));
        put(result, "state", DiscordPlaceholders.render(settings.state, values, settings));
        JsonObject assets = new JsonObject();
        if (settings.largeImageKey != null && !settings.largeImageKey.isBlank()) {
            put(assets, "large_image", settings.largeImageKey.trim());
            put(assets, "large_text", DiscordPlaceholders.render(settings.largeImageText, values, settings));
        }
        if (settings.smallImageKey != null && !settings.smallImageKey.isBlank()) {
            put(assets, "small_image", settings.smallImageKey.trim());
            put(assets, "small_text", DiscordPlaceholders.render(settings.smallImageText, values, settings));
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
        if (settings.sharePlayer) {
            values.put("player", minecraft.getUser().getName());
            values.put("custom_name", NameCustomizer.customizeName(minecraft.getUser().getName()).getString());
        }
        if (settings.shareArea) values.put("area", area(minecraft));
        if (settings.shareProfile) values.put("profile", cfg.presets.currentPreset);
        var server = minecraft.getCurrentServer();
        if (settings.shareServer) values.put("server", server == null ? minecraft.player == null ? "Main menu" : "Singleplayer" : server.ip);
        values.put("game", "Minecraft");
        values.put("version", SharedConstants.getCurrentVersion().name());
        long minutes = Math.max(0, (System.nanoTime() - SESSION_NANOS) / 60_000_000_000L);
        values.put("session", minutes < 60 ? minutes + "m" : minutes / 60 + "h " + minutes % 60 + "m");
        if (settings.sharePerformance) values.put("fps", Integer.toString(minecraft.getFps()));
        var player = minecraft.player;
        if (player == null) return values;
        if (settings.shareEquipment) {
            hand(values, player.getMainHandItem(), player.getOffhandItem());
            values.put("helmet", itemName(player.getItemBySlot(EquipmentSlot.HEAD)));
            values.put("chestplate", itemName(player.getItemBySlot(EquipmentSlot.CHEST)));
            values.put("leggings", itemName(player.getItemBySlot(EquipmentSlot.LEGS)));
            values.put("boots", itemName(player.getItemBySlot(EquipmentSlot.FEET)));
        }
        if (settings.shareStats) {
            values.put("health", number(player.getHealth()));
            values.put("max_health", number(player.getMaxHealth()));
            values.put("absorption", number(player.getAbsorptionAmount()));
            values.put("food", Integer.toString(player.getFoodData().getFoodLevel()));
            values.put("xp_level", Integer.toString(player.experienceLevel));
            values.put("xp_progress", Math.round(Math.clamp(player.experienceProgress, 0, 1) * 100) + "%");
        }
        if (settings.sharePerformance && minecraft.getConnection() != null) {
            var info = minecraft.getConnection().getPlayerInfo(player.getUUID());
            if (info != null) values.put("ping", Integer.toString(Math.max(0, info.getLatency())));
        }
        if (settings.sharePosition && minecraft.level != null) {
            var pos = player.blockPosition();
            values.put("x", Integer.toString(pos.getX()));
            values.put("y", Integer.toString(pos.getY()));
            values.put("z", Integer.toString(pos.getZ()));
            values.put("coords", pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
            values.put("dimension", minecraft.level.dimension().identifier().toString());
            values.put("biome", minecraft.level.getBiome(pos).unwrapKey().map(key -> key.identifier().toString()).orElse(""));
        }
        return values;
    }

    private static void hand(Map<String, String> values, ItemStack main, ItemStack offhand) {
        values.put("item", itemName(main));
        values.put("item_id", main.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(main.getItem()).toString());
        values.put("item_count", Integer.toString(main.getCount()));
        values.put("item_durability", main.isEmpty() ? "" : main.isDamageableItem()
                ? Math.max(0, main.getMaxDamage() - main.getDamageValue()) + "/" + main.getMaxDamage() : "N/A");
        values.put("offhand", itemName(offhand));
        values.put("offhand_count", Integer.toString(offhand.getCount()));
    }

    private static String itemName(ItemStack stack) { return stack.isEmpty() ? "Empty" : stack.getHoverName().getString(); }
    private static String number(float value) { return java.math.BigDecimal.valueOf(Math.round(value * 10) / 10.0).stripTrailingZeros().toPlainString(); }

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

}
