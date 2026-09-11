package de.flori.skycustomizer.util;

import de.flori.skycustomizer.config.ScoreboardStyles;
import de.flori.skycustomizer.config.SkyCustomizerConfig.ScoreboardConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;

final class ScoreboardImageChecks {
    private static int assertions;
    static void run() throws Exception {
        Path directory = Path.of("build", "scoreboard-checks"); Files.createDirectories(directory);
        Path fixture = directory.resolve("gradient.png");
        var image = new BufferedImage(1600, 900, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        g.setPaint(new GradientPaint(0, 0, new Color(0xE0894B), 1600, 900, new Color(0x203F65)));
        g.fillRect(0, 0, 1600, 900);
        g.setColor(new Color(255, 240, 205, 100)); g.fillOval(260, 90, 800, 800);
        g.setColor(new Color(23, 35, 59, 180));
        g.fillPolygon(new int[]{0, 700, 1100, 1600, 1600, 0}, new int[]{700, 180, 740, 400, 900, 900}, 6);
        g.dispose(); ImageIO.write(image, "png", fixture.toFile());
        var data = ScoreboardImageIO.read(fixture);
        check(data.width() == 1024 && data.height() == 576, "Images downsample with original aspect ratio");
        String asset = ScoreboardImageIO.fileName(data);
        check(asset.matches("[a-f0-9]{64}\\.png"), "Content-addressed local image name");
        check(asset.equals(ScoreboardImageIO.fileName(ScoreboardImageIO.read(fixture))), "Repeated import deduplicates");
        Path library = directory.resolve("imports");
        check(ScoreboardImageIO.store(library, data).equals(asset), "Import writes a normalized copy");
        Files.writeString(library.resolve(asset), "interrupted copy");
        ScoreboardImageIO.store(library, data);
        check(java.util.Arrays.equals(Files.readAllBytes(library.resolve(asset)), data.png()), "Re-import repairs corrupt cached copies");
        check(ScoreboardImageIO.resolve(directory, asset).getParent().equals(directory.toAbsolutePath()), "Imported image stays in library");
        for (String name : List.of("../test.png", "C:/secret.png", "http://test/x.png", "test.png", "", "../" + asset)) {
            boolean rejected = false;
            try { ScoreboardImageIO.resolve(directory, name); } catch (IOException expected) { rejected = true; }
            check(rejected, "Reject unsafe image reference");
        }
        Path broken = directory.resolve("broken.png"); Files.writeString(broken, "Not an image");
        boolean rejected = false;
        try { ScoreboardImageIO.read(broken); } catch (IOException expected) { rejected = true; }
        check(rejected, "Invalid image safely rejected");
        Path large = directory.resolve("too-large.png");
        try (var file = new java.io.RandomAccessFile(large.toFile(), "rw")) { file.setLength(ScoreboardImageIO.MAX_BYTES + 1L); }
        rejected = false;
        try { ScoreboardImageIO.read(large); } catch (IOException expected) { rejected = true; }
        check(rejected, "File-size limit checked before decoding");
        var transparent = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB); transparent.setRGB(1, 1, 0x8055AAFF);
        Path alpha = directory.resolve("alpha.png"); ImageIO.write(transparent, "png", alpha.toFile());
        var decoded = ImageIO.read(new java.io.ByteArrayInputStream(ScoreboardImageIO.read(alpha).png()));
        check(decoded.getRGB(0, 0) >>> 24 == 0 && decoded.getRGB(1, 1) >>> 24 == 128, "PNG transparency retained");
        var jpeg = new BufferedImage(10, 7, BufferedImage.TYPE_INT_RGB); Path photo = directory.resolve("photo.jpg");
        ImageIO.write(jpeg, "jpeg", photo.toFile()); check(ScoreboardImageIO.read(photo).width() == 10, "JPEG import supported");
        var cover = ScoreboardImageIO.fit(400, 200, 100, 200, "COVER");
        check(cover.u() == 150 && cover.sourceWidth() == 100 && cover.height() == 200, "Cover centered crop");
        var contain = ScoreboardImageIO.fit(400, 200, 100, 200, "CONTAIN");
        check(contain.y() == 75 && contain.height() == 50 && contain.sourceWidth() == 400, "Contain letterboxing");
        var stretch = ScoreboardImageIO.fit(400, 200, 100, 200, "STRETCH");
        check(stretch.height() == 200 && stretch.sourceWidth() == 400 && stretch.u() == 0, "Stretch fills card");

        var style = new ScoreboardConfig(); style.customLines.add("Legacy line"); ScoreboardLines.ensureKeys(style);
        String legacy = style.customLineKeys.getFirst(); ScoreboardLines.ensureKeys(style);
        check(style.customLineKeys.getFirst().equals(legacy), "Legacy custom-line IDs remain stable");
        String a = ScoreboardLines.add(style, "A"), b = ScoreboardLines.add(style, "B");
        ScoreboardLines.edit(style, a, "Edited"); ScoreboardLines.edit(style, "server:id", "Literal $1 \\ text");
        check(style.customLines.get(1).equals("Edited") && style.lineOverrides.get("server:id").equals("Literal $1 \\ text"), "Direct edits are literal, not replacement regex");
        ScoreboardLines.move(style, List.of("server:id", legacy, a, b), a, -1);
        check(style.lineOrder.equals(List.of("server:id", a, legacy, b)), "Rows move without rewriting text or IDs");
        style.hiddenLineIds.add(a); ScoreboardLines.remove(style, a);
        check(!style.lineOrder.contains(a) && !style.hiddenLineIds.contains(a) && style.customLineKeys.get(1).equals(b), "Delete preserves remaining IDs and clears metadata");
        style.backgroundImageEnabled = true; style.backgroundImage = asset; style.backgroundImageFit = "CONTAIN";
        style.backgroundImageOpacity = 174; style.backgroundImageShade = 88;
        var copy = ScoreboardStyles.copy(style);
        check(copy.backgroundImage.equals(asset) && copy.backgroundImageOpacity == 174 && copy.backgroundImageShade == 88, "Image settings copied into drafts");
        copy.lineOverrides.clear(); copy.customLineKeys.clear(); copy.lineOrder.clear();
        check(!style.lineOverrides.isEmpty() && !style.customLineKeys.isEmpty() && !style.lineOrder.isEmpty(), "Direct-edit draft isolation");
        ScoreboardStyles.applyScreenshot(style);
        check(style.backgroundImage.equals(asset) && style.backgroundImageFit.equals("CONTAIN"), "Visual presets preserve imported image choice");
        for (boolean title : new boolean[]{false, true}) for (int spacing = 0; spacing <= 5; spacing++) {
            style.lineSpacing = spacing;
            for (int i = 0; i < 15; i++) {
                int row = ScoreboardLines.rowY(style, title, i);
                check(ScoreboardLines.hit(style, title, 15, 140, 20, row + 4) == i, "Click maps to rendered line");
            }
            check(ScoreboardLines.hit(style, title, 15, 140, -1, 20) == -1, "Outside board is not clickable");
        }
        check(ScoreboardLines.hit(style, true, 15, 140, 20, style.padding + 2) == -2, "Title click is distinct from line click");
        Component original = TextUtil.parseLegacyFormatting("&ePurse: &6&l100&r &bBits", Style.EMPTY);
        Component roundTrip = TextUtil.parseLegacyFormatting(TextUtil.editableFormatting(original), Style.EMPTY);
        check(original.equals(roundTrip), "Editable text retains colors and formatting");
        System.out.println("Scoreboard images and editing: " + assertions + " checks passed.");
    }
    private static void check(boolean value, String message) { assertions++; if (!value) throw new AssertionError(message); }
}
