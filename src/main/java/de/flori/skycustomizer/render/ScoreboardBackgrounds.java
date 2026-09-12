package de.flori.skycustomizer.render;

import com.mojang.blaze3d.platform.NativeImage;
import de.flori.skycustomizer.config.SkyCustomizerConfig.ScoreboardConfig;
import de.flori.skycustomizer.util.ScoreboardImageIO;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

public final class ScoreboardBackgrounds {
    private static final int CACHE_CAPACITY = 24;
    private static final ExecutorService IO = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(32), task -> { var thread = new Thread(task, "SkyCustomizer images"); thread.setDaemon(true); return thread; });
    private static final Map<String, Entry> CACHE = new LinkedHashMap<>(8, .75f, true);
    private static volatile List<String> gallery = List.of();
    private static volatile long galleryUpdatedAt;
    private static final class Entry {
        Identifier texture;
        int width, height;
        String status = "Loading image...";
    }
    private static final class BackgroundTexture extends DynamicTexture {
        BackgroundTexture(NativeImage pixels) {
            super(() -> "SkyCustomizer scoreboard background", pixels);
            sampler = com.mojang.blaze3d.systems.RenderSystem.getSamplerCache()
                    .getClampToEdge(com.mojang.blaze3d.textures.FilterMode.LINEAR);
        }
    }
    private ScoreboardBackgrounds() {}

    public static Path directory() { return FabricLoader.getInstance().getConfigDir().resolve("skycustomizer/backgrounds"); }
    public static List<String> gallery() {
        long now = System.nanoTime();
        if (now - galleryUpdatedAt < 1_000_000_000L) return gallery;
        synchronized (ScoreboardBackgrounds.class) {
            if (now - galleryUpdatedAt < 1_000_000_000L) return gallery;
            try {
                if (!Files.isDirectory(directory())) gallery = List.of();
                else try (var files = Files.list(directory())) {
                    gallery = files.filter(Files::isRegularFile)
                            .sorted(Comparator.comparingLong(ScoreboardBackgrounds::modified).reversed())
                            .map(path -> path.getFileName().toString())
                            .filter(name -> name.matches("[a-f0-9]{64}\\.png"))
                            .limit(CACHE_CAPACITY)
                            .toList();
                }
            } catch (IOException ignored) { gallery = List.of(); }
            galleryUpdatedAt = now;
            return gallery;
        }
    }
    public static CompletableFuture<String> choose() {
        return CompletableFuture.supplyAsync(() -> {
            try (var stack = MemoryStack.stackPush()) {
                var filters = stack.mallocPointer(3);
                filters.put(stack.UTF8("*.png")).put(stack.UTF8("*.jpg")).put(stack.UTF8("*.jpeg")).flip();
                return TinyFileDialogs.tinyfd_openFileDialog("Choose scoreboard background", "", filters, "PNG / JPEG images", false);
            }
        }, IO).thenCompose(path -> path == null ? CompletableFuture.completedFuture(null) : importFile(Path.of(path)));
    }
    public static CompletableFuture<String> importFile(Path source) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                var data = ScoreboardImageIO.read(source);
                return ScoreboardImageIO.store(directory(), data);
            } catch (IOException e) { throw new CompletionException(e); }
        }, IO);
    }
    public static String status(String file) {
        if (file == null || file.isBlank()) return "No image imported";
        return entry(file).status;
    }
    public static boolean ready(String file) { return file != null && !file.isBlank() && entry(file).texture != null; }
    public static void retry(String file) {
        Entry entry = CACHE.get(file);
        if (entry != null && entry.texture == null) CACHE.remove(file);
    }
    private static Entry entry(String file) {
        Entry existing = CACHE.get(file);
        if (existing != null) return existing;
        var entry = new Entry();
        CACHE.put(file, entry);
        while (CACHE.size() > CACHE_CAPACITY) {
            var iterator = CACHE.entrySet().iterator();
            Entry removed = iterator.next().getValue(); iterator.remove();
            if (removed.texture != null) Minecraft.getInstance().getTextureManager().release(removed.texture);
        }
        CompletableFuture.supplyAsync(() -> {
            try { return ScoreboardImageIO.read(ScoreboardImageIO.resolve(directory(), file)); }
            catch (IOException e) { throw new CompletionException(e); }
        }, IO).whenComplete((data, error) -> Minecraft.getInstance().execute(() -> {
            if (CACHE.get(file) != entry) return;
            if (error != null) { entry.status = "Image unavailable - import it again"; return; }
            NativeImage pixels = null;
            DynamicTexture texture = null;
            try {
                pixels = NativeImage.read(data.png());
                texture = new BackgroundTexture(pixels);
                pixels = null;
                Identifier id = Identifier.fromNamespaceAndPath("skycustomizer", "background/" + file);
                Minecraft.getInstance().getTextureManager().register(id, texture);
                texture = null;
                entry.texture = id; entry.width = data.width(); entry.height = data.height();
                entry.status = "Ready: " + entry.width + " x " + entry.height;
            } catch (Exception e) { entry.status = "Image could not be uploaded"; }
            finally { if (pixels != null) pixels.close(); if (texture != null) texture.close(); }
        }));
        return entry;
    }
    public static boolean draw(GuiGraphicsExtractor g, ScoreboardConfig style, int width, int height) {
        if (!style.backgroundEnabled || !style.backgroundImageEnabled || style.backgroundImage == null || style.backgroundImage.isBlank()) return false;
        Entry entry = entry(style.backgroundImage);
        if (entry.texture == null) return false;
        var fit = ScoreboardImageIO.fit(entry.width, entry.height, width, height, style.backgroundImageFit);
        int color = (Math.clamp(style.backgroundImageOpacity, 0, 255) << 24) | 0xFFFFFF;
        if (!style.roundedBackground || width < 10 || height < 10) strip(g, entry, fit, 0, 0, width, height, color);
        else {
            strip(g, entry, fit, 3, 0, width - 3, 1, color);
            strip(g, entry, fit, 1, 1, width - 1, 3, color);
            strip(g, entry, fit, 0, 3, width, height - 3, color);
            strip(g, entry, fit, 1, height - 3, width - 1, height - 1, color);
            strip(g, entry, fit, 3, height - 1, width - 3, height, color);
        }
        return true;
    }
    public static boolean drawThumbnail(GuiGraphicsExtractor g, String file, int x, int y, int width, int height) {
        Entry entry = entry(file);
        if (entry.texture == null) return false;
        g.pose().pushMatrix();
        try {
            g.pose().translate(x, y);
            strip(g, entry, ScoreboardImageIO.fit(entry.width, entry.height, width, height, "COVER"), 0, 0, width, height, 0xFFFFFFFF);
        } finally {
            g.pose().popMatrix();
        }
        return true;
    }
    private static long modified(Path path) {
        try { return Files.getLastModifiedTime(path).toMillis(); }
        catch (IOException ignored) { return 0; }
    }
    private static void strip(GuiGraphicsExtractor g, Entry entry, ScoreboardImageIO.Fit fit,
                              int left, int top, int right, int bottom, int color) {
        int x = Math.max(left, Math.round(fit.x())), y = Math.max(top, Math.round(fit.y()));
        int r = Math.min(right, Math.round(fit.x() + fit.width())), b = Math.min(bottom, Math.round(fit.y() + fit.height()));
        if (r <= x || b <= y) return;
        float u = fit.u() + (x - fit.x()) / fit.width() * fit.sourceWidth();
        float v = fit.v() + (y - fit.y()) / fit.height() * fit.sourceHeight();
        int sw = Math.max(1, Math.round((r - x) / fit.width() * fit.sourceWidth()));
        int sh = Math.max(1, Math.round((b - y) / fit.height() * fit.sourceHeight()));
        g.blit(RenderPipelines.GUI_TEXTURED, entry.texture, x, y, u, v, r - x, b - y, sw, sh, entry.width, entry.height, color);
    }
}
