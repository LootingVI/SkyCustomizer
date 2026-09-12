package de.flori.skycustomizer.util;

public final class HudLayoutTest {
    private static int assertions;

    public static void main(String[] args) {
        var sidebar = HudLayout.scoreboard(427, 240, 140, 150, 1, 0, 0);
        near(sidebar.x() + sidebar.width(), 423, "right-edge anchor");
        near(sidebar.y() + sidebar.height() / 2, 120, "vertical center anchor");
        var actionbar = HudLayout.actionbar(427, 240, 180, 9, 1, 0, 0);
        near(actionbar.x() + actionbar.width() / 2, 213.5f, "actionbar horizontal center");
        near(actionbar.y(), 168, "vanilla actionbar baseline");

        for (boolean board : new boolean[] {false, true}) {
            for (int[] viewport : new int[][] {{320, 240}, {427, 240}, {854, 480}, {1920, 1080}}) {
                for (float scale : new float[] {.45f, 1, 1.35f, 3, Float.NaN, Float.POSITIVE_INFINITY}) {
                    for (int[] position : new int[][] {{16, 34}, {-4000, -4000}, {3000, 3000}, {100, 100}}) {
                        int w = board ? 147 : 221;
                        int h = board ? 173 : 9;
                        var saved = HudLayout.fromTopLeft(board, viewport[0], viewport[1], w, h,
                                scale, position[0], position[1], false);
                        var rendered = board
                                ? HudLayout.scoreboard(viewport[0], viewport[1], w, h, saved.scale(), saved.offsetX(), saved.offsetY())
                                : HudLayout.actionbar(viewport[0], viewport[1], w, h, saved.scale(), saved.offsetX(), saved.offsetY());
                        check(Float.isFinite(rendered.scale()) && rendered.scale() > 0, "finite positive scale");
                        check(rendered.x() >= 3.99f && rendered.y() >= 3.99f, "minimum margins");
                        check(rendered.x() + rendered.width() <= viewport[0] - 3.99f, "right clamp");
                        check(rendered.y() + rendered.height() <= viewport[1] - 3.99f, "bottom clamp");
                        var savedAgain = HudLayout.fromTopLeft(board, viewport[0], viewport[1], w, h,
                                saved.scale(), rendered.x(), rendered.y(), false);
                        check(saved.equals(savedAgain), "save/reopen must not drift");
                    }
                }
            }
        }

        var snapped = HudLayout.fromTopLeft(true, 800, 600, 140, 160, 1, 331, 222, true);
        var aligned = HudLayout.scoreboard(800, 600, 140, 160, snapped.scale(), snapped.offsetX(), snapped.offsetY());
        near(aligned.x() + aligned.width() / 2, 400, "snap horizontal center");
        near(aligned.y() + aligned.height() / 2, 300, "snap vertical center");
        var grid = HudLayout.fromTopLeft(true, 800, 600, 140, 160, 1, 101, 117, true);
        var gridded = HudLayout.scoreboard(800, 600, 140, 160, grid.scale(), grid.offsetX(), grid.offsetY());
        near(gridded.x(), 104, "eight-pixel grid X");
        near(gridded.y(), 120, "eight-pixel grid Y");
        near(HudLayout.scaleForHandle(280, 320, 140, 160), 2, "aspect ratio resize");
        near(HudLayout.scaleForHandle(-100, -100, 140, 160), .45f, "resize lower limit");
        var resizedWindow = HudLayout.scoreboard(320, 240, 160, 200, 3, 800, -800);
        check(resizedWindow.height() <= 232, "large HUD fits after changing GUI scale");
        System.out.println("HUD geometry: " + assertions + " checks passed.");
        ScoreboardStylesTest.run();
        CustomizationChecks.run();
        NameReplacementChecks.run();
        try { ScoreboardImageChecks.run(); } catch (Exception e) { throw new AssertionError(e); }
        try { de.flori.skycustomizer.config.StudioChecks.run(); }
        catch(Exception e){throw new AssertionError("Studio checks failed",e);}
        try { de.flori.skycustomizer.discord.DiscordRpcChecks.run(); }
        catch(Exception e){throw new AssertionError("Discord RPC checks failed",e);}
    }

    private static void near(float actual, float expected, String message) {
        check(Math.abs(actual - expected) <= .51f, message + ": " + actual + " != " + expected);
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
