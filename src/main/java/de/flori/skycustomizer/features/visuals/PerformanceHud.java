package de.flori.skycustomizer.features.visuals;

import de.flori.skycustomizer.config.SkyCustomizerConfig;
import de.flori.skycustomizer.config.HudEditorScreen;
import de.flori.skycustomizer.util.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PerformanceHud {
    private static long lastTick;
    private static double localTps = 20.0;

    private PerformanceHud() {}

    public static void tick() {
        long now = System.nanoTime();
        if (lastTick != 0) {
            double elapsed = Math.max(1, now - lastTick) / 1_000_000_000.0;
            double instant = Math.clamp(1.0 / elapsed, 0.0, 20.0);
            localTps += (instant - localTps) * 0.15;
        }
        lastTick = now;
    }

    public static void draw(GuiGraphicsExtractor graphics) {
        if (graphics == null) return;
        var cfg = SkyCustomizerConfig.get();
        var settings = cfg.visuals;
        if (!cfg.enabled || !settings.enabled || !settings.performanceHud) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof HudEditorScreen) return;
        if (minecraft.font == null) return;
        List<String> values = new ArrayList<>(3);
        if (settings.performanceShowFps) values.add("FPS " + minecraft.getFps());
        if (settings.performanceShowPing) values.add("PING " + ping(minecraft) + " ms");
        if (settings.performanceShowTps) values.add(String.format(Locale.ROOT, "TPS %.1f", localTps));
        if (values.isEmpty()) return;
        String text = String.join("  |  ", values);
        float scale = Float.isFinite(settings.performanceHudScale)
                ? Math.clamp(settings.performanceHudScale, 0.5f, 3.0f) : 1.0f;
        int textWidth = minecraft.font.width(text);
        int baseWidth = textWidth + 12;
        int baseHeight = 19;
        int x = Math.clamp(4 + settings.performanceHudOffsetX, 0,
                Math.max(0, graphics.guiWidth() - Math.round(baseWidth * scale)));
        int y = Math.clamp(4 + settings.performanceHudOffsetY, 0,
                Math.max(0, graphics.guiHeight() - Math.round(baseHeight * scale)));
        int textColor = 0xFF000000 | (ColorUtil.fromHex(settings.performanceHudColorHex) & 0xFFFFFF);
        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate(x, y);
            graphics.pose().scale(scale, scale);
            if (settings.performanceHudBackground) {
                int alpha = Math.clamp(settings.performanceHudBackgroundOpacity, 0, 255);
                graphics.fill(0, 0, baseWidth, baseHeight, (alpha << 24) | 0x130D09);
            }
            graphics.text(minecraft.font, Component.literal(text), 6, 5, textColor, false);
        } finally {
            graphics.pose().popMatrix();
        }
    }

    private static int ping(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.getConnection() == null) return 0;
        var info = minecraft.getConnection().getPlayerInfo(minecraft.player.getUUID());
        return info == null ? 0 : Math.max(0, info.getLatency());
    }
}
