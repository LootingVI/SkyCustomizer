package de.flori.skycustomizer.util;

public final class HudLayout {
    public static final int GRID = 8;
    public static final int MARGIN = 4;
    public static final int ACTIONBAR_BOTTOM = 63;
    public static final float MIN_SCALE = 0.45f;
    public static final float MAX_SCALE = 3.0f;

    private HudLayout() {}

    public record Bounds(float x, float y, float width, float height, float scale) {}

    public record Placement(int offsetX, int offsetY, float scale) {}

    public static Bounds scoreboard(int vw, int vh, int baseW, int baseH,
                                    float scale, int offsetX, int offsetY) {
        float fitted = fitScale(vw, vh, baseW, baseH, scale);
        float w = Math.max(1, baseW) * fitted;
        float h = Math.max(1, baseH) * fitted;
        return clampBounds(vw, vh, vw - MARGIN + (float) offsetX - w,
                vh / 2.0f + offsetY - h / 2, w, h, fitted);
    }

    public static Bounds actionbar(int vw, int vh, int baseW, int baseH,
                                   float scale, int offsetX, int offsetY) {
        float fitted = fitScale(vw, vh, baseW, baseH, scale);
        float w = Math.max(1, baseW) * fitted;
        float h = Math.max(1, baseH) * fitted;
        return clampBounds(vw, vh, vw / 2.0f + offsetX - w / 2,
                vh - ACTIONBAR_BOTTOM + (float) offsetY - h, w, h, fitted);
    }

    public static Placement fromTopLeft(boolean scoreboard, int vw, int vh, int baseW, int baseH,
                                        float scale, float left, float top, boolean snap) {
        float fitted = fitScale(vw, vh, baseW, baseH, scale);
        float w = Math.max(1, baseW) * fitted;
        float h = Math.max(1, baseH) * fitted;
        left = finite(left, MARGIN);
        top = finite(top, MARGIN);
        if (snap) {
            left = snapAxis(left, w, vw);
            top = snapAxis(top, h, vh);
        }
        Bounds b = clampBounds(vw, vh, left, top, w, h, fitted);
        return scoreboard
                ? new Placement(Math.round(b.x + w - (vw - MARGIN)), Math.round(b.y + h / 2 - vh / 2.0f), fitted)
                : new Placement(Math.round(b.x + w / 2 - vw / 2.0f), Math.round(b.y + h - (vh - ACTIONBAR_BOTTOM)), fitted);
    }

    public static float scaleForHandle(float deltaX, float deltaY, int baseW, int baseH) {
        double w = Math.max(1, baseW), h = Math.max(1, baseH);
        float scale = (float) ((finite(deltaX, (float) w) * w + finite(deltaY, (float) h) * h) / (w * w + h * h));
        return Math.clamp(scale, MIN_SCALE, MAX_SCALE);
    }

    public static float fitScale(int vw, int vh, int baseW, int baseH, float scale) {
        float wanted = Math.clamp(finite(scale, 1), MIN_SCALE, MAX_SCALE);
        float availableW = Math.max(1, vw - MARGIN * 2);
        float availableH = Math.max(1, vh - MARGIN * 2);
        return Math.min(wanted, Math.min(availableW / Math.max(1, baseW), availableH / Math.max(1, baseH)));
    }

    private static Bounds clampBounds(int vw, int vh, float x, float y, float w, float h, float scale) {
        float minX = Math.min(MARGIN, Math.max(0, (vw - w) / 2));
        float minY = Math.min(MARGIN, Math.max(0, (vh - h) / 2));
        return new Bounds(Math.clamp(x, minX, Math.max(minX, vw - minX - w)),
                Math.clamp(y, minY, Math.max(minY, vh - minY - h)), w, h, scale);
    }

    private static float snapAxis(float position, float size, int viewport) {

        float[] guides = { MARGIN, (viewport - size) / 2.0f, viewport - MARGIN - size };
        float nearest = position;
        float distance = 6.01f;
        for (float guide : guides) {
            float candidateDistance = Math.abs(position - guide);
            if (candidateDistance < distance) {
                nearest = guide;
                distance = candidateDistance;
            }
        }
        return distance < 6.01f ? nearest : Math.round(position / GRID) * (float) GRID;
    }

    private static float finite(float value, float fallback) {
        return Float.isFinite(value) ? value : fallback;
    }
}
