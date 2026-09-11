package de.flori.skycustomizer.util;

import de.flori.skycustomizer.config.SkyCustomizerConfig.ScoreboardConfig;
import de.flori.skycustomizer.features.ScoreboardCustomizer;
import java.util.*;

public final class ScoreboardLines {
    private ScoreboardLines() {}
    public static void ensureKeys(ScoreboardConfig style) {
        while (style.customLineKeys.size() > style.customLines.size()) style.customLineKeys.removeLast();
        var used = new HashSet<String>();
        for (int i = 0; i < style.customLines.size(); i++) {
            String key = i < style.customLineKeys.size() ? style.customLineKeys.get(i) : null;
            if (key == null || !key.startsWith("custom:") || !used.add(key)) {
                key = "custom:" + UUID.randomUUID(); used.add(key);
                if (i < style.customLineKeys.size()) style.customLineKeys.set(i, key); else style.customLineKeys.add(key);
            }
        }
    }
    public static String add(ScoreboardConfig style, String text) {
        ensureKeys(style);
        String id = "custom:" + UUID.randomUUID();
        style.customLines.add(text); style.customLineKeys.add(id);
        return id;
    }
    public static void edit(ScoreboardConfig style, String id, String text) {
        int custom = style.customLineKeys.indexOf(id);
        if (custom >= 0 && custom < style.customLines.size()) style.customLines.set(custom, text);
        else style.lineOverrides.put(id, text);
    }
    public static boolean remove(ScoreboardConfig style, String id) {
        int custom = style.customLineKeys.indexOf(id);
        if (custom < 0 || custom >= style.customLines.size()) return false;
        style.customLines.remove(custom); style.customLineKeys.remove(custom);
        style.lineOverrides.remove(id); style.hiddenLineIds.remove(id); style.lineOrder.remove(id);
        return true;
    }
    public static void move(ScoreboardConfig style, List<String> visibleIds, String id, int direction) {
        var order = new ArrayList<>(visibleIds);
        int from = order.indexOf(id), to = from + direction;
        if (from < 0 || to < 0 || to >= order.size()) return;
        Collections.swap(order, from, to);
        for (String saved : style.lineOrder) if (!order.contains(saved)) order.add(saved);
        style.lineOrder = order;
    }
    public static int rowY(ScoreboardConfig style, boolean title, int line) {
        int padding = Math.clamp(style.padding, 2, 14);
        int lineHeight = ScoreboardCustomizer.lineHeight(style);
        return padding + (title ? 9 + (style.compactStyle ? 2 : 4) : 0)
                + line * (lineHeight + Math.clamp(style.lineSpacing, 0, 5));
    }
    public static int hit(ScoreboardConfig style, boolean title, int count, int boardWidth,
                          double localX, double localY) {
        if (localX < 0 || localX >= boardWidth) return -1;
        int padding = Math.clamp(style.padding, 2, 14);
        if (title && localY >= padding && localY < padding + 9) return -2;
        double dy = localY - rowY(style, title, 0);
        if (dy < 0) return -1;
        int index = (int) dy / (ScoreboardCustomizer.lineHeight(style) + Math.clamp(style.lineSpacing, 0, 5));
        return index < count ? index : -1;
    }
}
