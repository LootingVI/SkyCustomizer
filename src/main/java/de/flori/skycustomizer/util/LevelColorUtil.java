package de.flori.skycustomizer.util;

import java.util.NavigableMap;
import java.util.TreeMap;

public final class LevelColorUtil {

    private LevelColorUtil() {}

    public static final String GRAY = "§7";
    public static final String WHITE = "§f";
    public static final String YELLOW = "§e";
    public static final String GREEN = "§a";
    public static final String DARK_GREEN = "§2";
    public static final String AQUA = "§b";
    public static final String DARK_AQUA = "§3";
    public static final String BLUE = "§9";
    public static final String LIGHT_PURPLE = "§d";
    public static final String DARK_PURPLE = "§5";
    public static final String GOLD = "§6";
    public static final String RED = "§c";
    public static final String DARK_RED = "§4";

    public static final String HEX_GRAY = "#AAAAAA";
    public static final String HEX_WHITE = "#FFFFFF";
    public static final String HEX_YELLOW = "#FFFF55";
    public static final String HEX_GREEN = "#55FF55";
    public static final String HEX_DARK_GREEN = "#00AA00";
    public static final String HEX_AQUA = "#55FFFF";
    public static final String HEX_DARK_AQUA = "#00AAAA";
    public static final String HEX_BLUE = "#5555FF";
    public static final String HEX_LIGHT_PURPLE = "#FF55FF";
    public static final String HEX_DARK_PURPLE = "#AA00AA";
    public static final String HEX_GOLD = "#FFAA00";
    public static final String HEX_RED = "#FF5555";
    public static final String HEX_DARK_RED = "#AA0000";

    public static NavigableMap<Integer, String> getOriginalLevelColorMap() {
        TreeMap<Integer, String> map = new TreeMap<>();
        map.put(0, GRAY);
        map.put(40, WHITE);
        map.put(80, YELLOW);
        map.put(120, GREEN);
        map.put(160, DARK_GREEN);
        map.put(200, AQUA);
        map.put(240, DARK_AQUA);
        map.put(280, BLUE);
        map.put(320, LIGHT_PURPLE);
        map.put(360, DARK_PURPLE);
        map.put(400, GOLD);
        map.put(440, RED);
        map.put(480, DARK_RED);
        return map;
    }

    public static NavigableMap<Integer, String> getOriginalHexMap() {
        TreeMap<Integer, String> map = new TreeMap<>();
        map.put(0, HEX_GRAY);
        map.put(40, HEX_WHITE);
        map.put(80, HEX_YELLOW);
        map.put(120, HEX_GREEN);
        map.put(160, HEX_DARK_GREEN);
        map.put(200, HEX_AQUA);
        map.put(240, HEX_DARK_AQUA);
        map.put(280, HEX_BLUE);
        map.put(320, HEX_LIGHT_PURPLE);
        map.put(360, HEX_DARK_PURPLE);
        map.put(400, HEX_GOLD);
        map.put(440, HEX_RED);
        map.put(480, HEX_DARK_RED);
        return map;
    }

    public static NavigableMap<Integer, String> getDefaultExtendedHexMap() {
        TreeMap<Integer, String> map = new TreeMap<>(getOriginalHexMap());

        map.put(520, "#FF5555");
        map.put(540, "#FF55FF");
        map.put(560, "#AA00AA");
        map.put(580, "#5555FF");
        map.put(600, "#FFAA00");

        map.put(650, "#55FFFF");
        map.put(700, "#FFFF55");
        return map;
    }

    public static String getOriginalColorForLevel(int level) {
        var map = getOriginalLevelColorMap();
        var entry = map.floorEntry(level);
        return entry != null ? entry.getValue() : GRAY;
    }

    public static String sectionToHex(String section) {
        return switch (section) {
            case "§7" -> HEX_GRAY;
            case "§f" -> HEX_WHITE;
            case "§e" -> HEX_YELLOW;
            case "§a" -> HEX_GREEN;
            case "§2" -> HEX_DARK_GREEN;
            case "§b" -> HEX_AQUA;
            case "§3" -> HEX_DARK_AQUA;
            case "§9" -> HEX_BLUE;
            case "§d" -> HEX_LIGHT_PURPLE;
            case "§5" -> HEX_DARK_PURPLE;
            case "§6" -> HEX_GOLD;
            case "§c" -> HEX_RED;
            case "§4" -> HEX_DARK_RED;
            default -> HEX_GRAY;
        };
    }

}
