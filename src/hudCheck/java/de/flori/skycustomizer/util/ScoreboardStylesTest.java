package de.flori.skycustomizer.util;

import de.flori.skycustomizer.config.ScoreboardStyles;
import de.flori.skycustomizer.config.SkyCustomizerConfig.ScoreboardConfig;
import java.util.List;
import java.util.function.Consumer;

final class ScoreboardStylesTest {
    static void run() {
        ScoreboardConfig saved = new ScoreboardConfig();
        saved.enabled = false;
        saved.offsetX = -187;
        saved.offsetY = 37;
        saved.scale = 1.4f;
        saved.hiddenLineContains.add("Hidden line");
        saved.renameRules.put("Original", "Replacement");
        saved.customLines.add("&aMy custom line");
        ScoreboardConfig draft = ScoreboardStyles.copy(saved);
        draft.hiddenLineContains.clear();
        draft.renameRules.clear();
        draft.customLines.clear();
        if (saved.hiddenLineContains.isEmpty() || saved.renameRules.isEmpty() || saved.customLines.isEmpty()) {
            throw new AssertionError("Discarding a style draft must preserve saved line rules");
        }
        List<Consumer<ScoreboardConfig>> presets = List.of(ScoreboardStyles::applyScreenshot,
                ScoreboardStyles::applyMinimal, ScoreboardStyles::applyDark);
        for (var preset : presets) {
            ScoreboardConfig style = ScoreboardStyles.copy(saved);
            preset.accept(style);
            if (style.offsetX != saved.offsetX || style.offsetY != saved.offsetY || style.scale != saved.scale
                    || style.enabled != saved.enabled || !style.hiddenLineContains.equals(saved.hiddenLineContains)
                    || !style.renameRules.equals(saved.renameRules) || !style.customLines.equals(saved.customLines)) {
                throw new AssertionError("A visual preset must not overwrite placement, enable state or content rules");
            }
        }

        ScoreboardConfig copy = ScoreboardStyles.copy(saved);
        try {
            for (var field : ScoreboardConfig.class.getFields()) {
                if (!java.util.Objects.equals(field.get(saved), field.get(copy))) {
                    throw new AssertionError("Style draft lost field " + field.getName());
                }
            }
        } catch (IllegalAccessException e) {
            throw new AssertionError(e);
        }
        System.out.println("Scoreboard presets: draft isolation and content/placement preservation passed.");
    }
}
