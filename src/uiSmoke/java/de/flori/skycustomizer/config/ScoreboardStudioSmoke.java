package de.flori.skycustomizer.config;

import de.flori.skycustomizer.features.ScoreboardCustomizer;
import de.flori.skycustomizer.render.ScoreboardBackgrounds;
import de.flori.skycustomizer.util.ScoreboardLines;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import java.nio.file.Path;
import java.util.List;

final class ScoreboardStudioSmoke {
    private static final Path FIXTURE = Path.of("..", "scoreboard-checks", "gradient.png");
    private static String imported;
    private static Throwable importError;
    private static SkyCustomizerConfig.ScoreboardConfig saved;
    private static int assertions;

    static void prepare() {
        ScoreboardBackgrounds.importFile(FIXTURE).whenComplete((file, error) -> Minecraft.getInstance().execute(() -> {
            imported = file; importError = error;
        }));
    }
    static ScoreboardEditorScreen screen(Minecraft mc, boolean compact, int page) {
        mc.options.guiScale().set(compact ? 4 : 2); mc.resizeGui();
        var style = new SkyCustomizerConfig.ScoreboardConfig(); ScoreboardStyles.applyScreenshot(style);
        style.backgroundImage = imported == null ? "" : imported; style.backgroundImageEnabled = true; style.backgroundImageShade = 85;
        var screen = new ScoreboardEditorScreen(null, style, result -> saved = result);
        try { set(screen, "page", page); set(screen, "compactOptions", compact && page != 0); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
        return screen;
    }
    static void drop(ScoreboardEditorScreen screen) { screen.onFilesDrop(List.of(FIXTURE)); }
    static void check(ScoreboardEditorScreen screen) throws Exception {
        check(importError == null && imported != null, "Background image import completed");
        check(ScoreboardBackgrounds.gallery().contains(imported), "Imported image appears in the gallery");
        check(ScoreboardBackgrounds.ready(imported), "Image uploaded as a renderable texture");
        var style = (SkyCustomizerConfig.ScoreboardConfig)get(screen, "staged");
        check(style.backgroundImage.equals(imported) && style.backgroundImageEnabled, "Drag-and-drop keeps imported image in draft");
        String before = screen.captureDraft();
        float x = (float)get(screen, "boardX"), y = (float)get(screen, "boardY"), scale = (float)get(screen, "boardScale");
        double mx = x + 15 * scale, my = y + (ScoreboardLines.rowY(style, true, 6) + 4) * scale;
        screen.mouseClicked(new MouseButtonEvent(mx, my, new MouseButtonInfo(0, 0)), false);
        check(get(screen, "selected").equals("sample:6"), "Click in preview selects actual purse row");
        EditBox input = (EditBox)get(screen, "inline");
        check(input.isFocused(), "Inline editor receives keyboard focus");
        input.setValue("&bMy purse: &6123 $1"); screen.checkpoint();
        var snapshot = ScoreboardCustomizer.snapshot(style);
        check(snapshot.lines().get(6).name().getString().equals("My purse: 123 $1"), "Direct row edit preserves literal dollar text");
        screen.undo(false); check(screen.captureDraft().equals(before), "Undo restores exact draft including image");
        screen.undo(true);
        style = (SkyCustomizerConfig.ScoreboardConfig)get(screen, "staged");
        check(style.lineOverrides.get("sample:6").equals("&bMy purse: &6123 $1"), "Redo restores direct edit");
        invoke(screen, "boldText", new Class<?>[0]);
        var bold = ScoreboardCustomizer.snapshot(style).lines().get(6).name();
        check(bold.getString().equals("My purse: 123 $1"), "Bold preserves visible text");
        check(bold.visit((format, text) -> !text.isEmpty() && !format.isBold() ? java.util.Optional.of(false)
                : java.util.Optional.<Boolean>empty(), net.minecraft.network.chat.Style.EMPTY).orElse(true), "Bold survives existing color resets");
        set(screen, "editing", false);
        screen.mouseClicked(new MouseButtonEvent(x + 15 * scale, y + (style.padding + 4) * scale, new MouseButtonInfo(0, 0)), false);
        check(get(screen, "selected").equals("@title"), "Click in preview selects title separately");
        ((EditBox)get(screen, "inline")).setValue("&6&lMY SKYBLOCK");
        check(ScoreboardCustomizer.snapshot(style).title().getString().equals("MY SKYBLOCK"), "Title supports direct formatted editing");
        invoke(screen, "addLine", new Class<?>[]{String.class}, "&bMy custom row");
        style = (SkyCustomizerConfig.ScoreboardConfig)get(screen, "staged");
        check(style.customLines.contains("&bMy custom row") && style.customLineKeys.size() == 1, "New custom row has stable identity");
        String newId = style.customLineKeys.getFirst();
        check(ScoreboardCustomizer.snapshot(style).lines().getFirst().id().equals(newId), "New line is placed visibly in board");
        invoke(screen, "save", new Class<?>[0]);
        check(saved != null && saved.backgroundImage.equals(imported) && saved.lineOverrides.containsKey("sample:6"), "Save commits both content and image settings");
        style.customLines.clear();
        check(saved.customLines.size() == 1, "Saved config is independent of remaining editor draft");
        style.customLines.add("&bMy custom row");
        set(screen, "page", 0);
        invoke(screen, "select", new Class<?>[]{String.class, boolean.class}, newId, true);
        System.out.println("[SkyCustomizer] SCOREBOARD_STUDIO_PASSED: " + assertions + " checks");
    }
    static void inlineExample(ScoreboardEditorScreen screen) throws Exception {
        invoke(screen, "select", new Class<?>[]{String.class, boolean.class}, "sample:6", true);
    }
    private static Object get(Object owner, String name) throws ReflectiveOperationException { var field = owner.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(owner); }
    private static void set(Object owner, String name, Object value) throws ReflectiveOperationException { var field = owner.getClass().getDeclaredField(name); field.setAccessible(true); field.set(owner, value); }
    private static void invoke(Object owner, String name, Class<?>[] types, Object... args) throws ReflectiveOperationException { var method = owner.getClass().getDeclaredMethod(name, types); method.setAccessible(true); method.invoke(owner, args); }
    private static void check(boolean value, String message) { assertions++; if (!value) throw new AssertionError(message); }
}
