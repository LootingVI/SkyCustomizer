package de.flori.skycustomizer.util;

import java.awt.Color;

public final class ColorUtil {
    private ColorUtil() {}

    public static int rainbow(float hueOffset, float saturation, float brightness) {
        float hue = (System.currentTimeMillis() % 3000L) / 3000.0f + hueOffset;
        hue %= 1.0f;
        return Color.HSBtoRGB(hue, saturation, brightness);
    }

    public static final long CHROMA_PERIOD_MS = 4000L;

    public static final long CHROMA_CHAR_STEP_MS = 90L;

    public static int chroma(float hueOffset, float speed) {
        long now = System.currentTimeMillis();
        float cycle = (float) ((now * (double) speed % CHROMA_PERIOD_MS) / CHROMA_PERIOD_MS);
        float hue = (cycle + hueOffset) % 1.0f;
        if (hue < 0) hue += 1.0f;
        return Color.HSBtoRGB(hue, 1.0f, 1.0f);
    }

    public static int chromaChar(int charIndex, float speed) {
        long now = (long) (System.currentTimeMillis() * (double) speed);
        long t = now - (long) charIndex * CHROMA_CHAR_STEP_MS;
        float hue = 1.0f - (float) (t % CHROMA_PERIOD_MS) / (float) CHROMA_PERIOD_MS;
        hue %= 1.0f;
        if (hue < 0) hue += 1.0f;
        return Color.HSBtoRGB(hue, 1.0f, 1.0f);
    }

    public static String toHex(int rgb) {
        return String.format("#%06X", (0xFFFFFF & rgb));
    }

    public static int fromHex(String hex) {
        try {
            String clean = hex.replace("#", "").trim();
            if (clean.length() == 6) return Integer.parseInt(clean, 16) | 0xFF000000;
            if (clean.length() == 8) return (int) Long.parseLong(clean, 16);
        } catch (Exception ignored) {}
        return 0xFFFFFFFF;
    }

    public static int lerp(String hexA, String hexB, float t) {
        int a = fromHex(hexA) & 0xFFFFFF;
        int b = fromHex(hexB) & 0xFFFFFF;
        int r = (int) (( (a >> 16 & 0xFF) * (1 - t) + (b >> 16 & 0xFF) * t));
        int g = (int) (( (a >> 8 & 0xFF) * (1 - t) + (b >> 8 & 0xFF) * t));
        int bC = (int) (( (a & 0xFF) * (1 - t) + (b & 0xFF) * t));
        return (r << 16) | (g << 8) | bC;
    }
}
