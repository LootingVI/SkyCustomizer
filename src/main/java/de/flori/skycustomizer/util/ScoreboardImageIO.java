package de.flori.skycustomizer.util;

import javax.imageio.ImageIO;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;

public final class ScoreboardImageIO {
    public static final int MAX_BYTES = 12 * 1024 * 1024;
    public record ImageData(byte[] png, int width, int height) {}
    public record Fit(float x, float y, float width, float height, float u, float v, float sourceWidth, float sourceHeight) {}
    private ScoreboardImageIO() {}

    public static ImageData read(Path path) throws IOException {
        if (!Files.isRegularFile(path) || Files.size(path) > MAX_BYTES) throw new IOException("Choose a PNG or JPEG up to 12 MB.");
        byte[] bytes;
        try (var input = Files.newInputStream(path)) { bytes = input.readNBytes(MAX_BYTES + 1); }
        if (bytes.length > MAX_BYTES) throw new IOException("Image exceeds 12 MB.");
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("This is not a supported image.");
            var reader = readers.next();
            try {
                String type = reader.getFormatName();
                if (!type.equalsIgnoreCase("png") && !type.equalsIgnoreCase("jpeg")) throw new IOException("Only PNG and JPEG images are supported.");
                reader.setInput(input, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > 8192 || height > 8192 || (long) width * height > 16_777_216)
                    throw new IOException("Image dimensions exceed 8192 px or 16 megapixels.");
                BufferedImage source = reader.read(0);
                float scale = Math.min(1, 1024f / Math.max(width, height));
                int w = Math.max(1, Math.round(width * scale)), h = Math.max(1, Math.round(height * scale));
                BufferedImage resized = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                var graphics = resized.createGraphics();
                try { graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    graphics.drawImage(source, 0, 0, w, h, null);
                } finally { graphics.dispose(); source.flush(); }
                var output = new ByteArrayOutputStream();
                try { if (!ImageIO.write(resized, "png", output)) throw new IOException("PNG encoder unavailable."); }
                finally { resized.flush(); }
                return new ImageData(output.toByteArray(), w, h);
            } finally { reader.dispose(); }
        }
    }
    public static String fileName(ImageData data) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data.png())) + ".png"; }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    public static String store(Path directory, ImageData data) throws IOException {
        Files.createDirectories(directory);
        String name = fileName(data);
        Path target = resolve(directory, name);
        if (Files.isRegularFile(target) && Files.size(target) == data.png().length
                && java.util.Arrays.equals(Files.readAllBytes(target), data.png())) return name;
        Path temporary = Files.createTempFile(directory, "import-", ".tmp");
        try {
            Files.write(temporary, data.png());
            try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
        return name;
    }
    public static Path resolve(Path directory, String fileName) throws IOException {
        if (fileName == null || !fileName.matches("[a-f0-9]{64}\\.png")) throw new IOException("Invalid background image reference.");
        Path root = directory.toAbsolutePath().normalize(), path = root.resolve(fileName).normalize();
        if (!path.getParent().equals(root) || Files.isSymbolicLink(path)) throw new IOException("Background must be a local imported image.");
        return path;
    }
    public static Fit fit(int iw, int ih, int bw, int bh, String mode) {
        if (iw <= 0 || ih <= 0 || bw <= 0 || bh <= 0) throw new IllegalArgumentException("Positive dimensions required");
        if ("STRETCH".equals(mode)) return new Fit(0, 0, bw, bh, 0, 0, iw, ih);
        float scale = "CONTAIN".equals(mode) ? Math.min(bw / (float) iw, bh / (float) ih) : Math.max(bw / (float) iw, bh / (float) ih);
        if ("CONTAIN".equals(mode)) return new Fit((bw - iw * scale) / 2, (bh - ih * scale) / 2, iw * scale, ih * scale, 0, 0, iw, ih);
        float sw = bw / scale, sh = bh / scale;
        return new Fit(0, 0, bw, bh, (iw - sw) / 2, (ih - sh) / 2, sw, sh);
    }
}
