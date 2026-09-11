package de.flori.skycustomizer.util;

import java.util.Arrays;

public final class FrameTimings {
    private static final double[][] samples = new double[2][1200];
    private static final int[] counts = new int[2], cursor = new int[2];
    private static long last;
    private static boolean wasMenu;
    private static int warmup = 60;
    private static boolean recording;
    public record Stats(int count, double meanMs, double p95Ms, double fps) {}
    private FrameTimings() {}
    public static synchronized void start() { clear(); recording = true; }
    public static synchronized void stop() { recording = false; last = 0; }
    public static synchronized boolean recording() { return recording; }
    public static synchronized void clear() { Arrays.fill(counts, 0); Arrays.fill(cursor, 0); last = 0; warmup = 60; }
    public static synchronized void frame(long now, boolean menu, boolean inWorld) {
        if (!recording || !inWorld) { last = 0; return; }
        if (last == 0 || menu != wasMenu) { last = now; wasMenu = menu; warmup = 60; return; }
        double ms = (now - last) / 1_000_000d; last = now;
        if (warmup-- > 0 || ms <= 0 || ms > 2000) return;
        int group = menu ? 1 : 0;
        samples[group][cursor[group]] = ms;
        cursor[group] = (cursor[group] + 1) % samples[group].length;
        counts[group] = Math.min(samples[group].length, counts[group] + 1);
    }
    public static synchronized Stats stats(boolean menu) {
        int group = menu ? 1 : 0, count = counts[group];
        if (count == 0) return new Stats(0,0,0,0);
        double[] sorted = Arrays.copyOf(samples[group], count); Arrays.sort(sorted);
        double mean = Arrays.stream(sorted).average().orElse(0);
        return new Stats(count, mean, sorted[Math.min(count-1, (int)Math.ceil(count*.95)-1)], 1000/mean);
    }
}
