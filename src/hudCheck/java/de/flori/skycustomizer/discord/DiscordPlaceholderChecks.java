package de.flori.skycustomizer.discord;

import de.flori.skycustomizer.config.SkyCustomizerConfig;

import java.util.HashSet;
import java.util.Map;

public final class DiscordPlaceholderChecks {
    private static int checks;

    public static void run() throws Exception {
        var settings = new SkyCustomizerConfig.DiscordConfig();
        check(!settings.sharePosition && !settings.shareServer, "Location and server address remain opt-in");
        var keys = new HashSet<String>();
        for (var entry : DiscordPlaceholders.ALL) {
            check(keys.add(entry.key()), "Unique placeholder: " + entry.token());
            String value = DiscordPlaceholders.preview(entry.token(), settings);
            check(value.equals(entry.privacy().allowed(settings) ? entry.example() : ""), "Example follows privacy: " + entry.token());
        }
        Map<String, String> values = Map.of("player", "ActualPlayer", "item", "§6Blade", "item_count", "2", "server", "private.example");
        check(DiscordPlaceholders.render("{player} uses {item} x{item_count}", values, settings).equals("ActualPlayer uses Blade x2"), "Multiple live values and formatting cleanup");
        check(DiscordPlaceholders.render("{item} / {item}", values, settings).equals("Blade / Blade"), "Repeated placeholders");
        check(DiscordPlaceholders.render("{coords}{server}", values, settings).isEmpty(), "Private values cannot leak through templates");
        check(DiscordPlaceholders.render("{item}", Map.of("item", "{server}"), settings).equals("{server}"), "Values are not recursively expanded");
        check(DiscordPlaceholders.render("{item}", Map.of("item", "$1 \\"), settings).equals("$1 \\"), "Values are literal, not regex replacements");
        check(DiscordPlaceholders.render("{unknown}", values, settings).equals("{unknown}"), "Unknown tokens remain visible for correction");
        check(DiscordPlaceholders.render(null, values, settings).isEmpty(), "Null template");
        check(DiscordPlaceholders.render("{health}{item}", Map.of(), settings).isEmpty(), "Unavailable values are empty");
        check(DiscordPlaceholders.render("  Hello\n\t{player}  ", values, settings).equals("Hello ActualPlayer"), "Single-line whitespace");
        check(DiscordPlaceholders.render("x".repeat(127) + "😀extra", values, settings).equals("x".repeat(127) + "😀"), "128-codepoint Unicode limit");
        for (String key : new String[]{"sharePlayer", "shareArea", "shareProfile", "shareServer", "shareEquipment", "shareStats", "sharePerformance", "sharePosition"}) {
            var field = settings.getClass().getField(key);
            field.setBoolean(settings, false);
        }
        for (var entry : DiscordPlaceholders.ALL) if (entry.privacy() != DiscordPlaceholders.Privacy.NONE)
            check(DiscordPlaceholders.preview(entry.token(), settings).isEmpty(), "All privacy groups enforced: " + entry.token());
        settings.shareEquipment = true;
        check(DiscordPlaceholders.preview("{item}", settings).equals("Hyperion"), "Equipment can be enabled independently");
        System.out.println("Discord placeholders: " + checks + " checks passed.");
    }

    private static void check(boolean result, String message) { checks++; if (!result) throw new AssertionError(message); }
}
