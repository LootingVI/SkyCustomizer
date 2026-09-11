package de.flori.skycustomizer.features;

import de.flori.skycustomizer.config.SkyCustomizerConfig;
import de.flori.skycustomizer.util.TextUtil;
import de.flori.skycustomizer.util.ComponentCache;
import net.minecraft.network.chat.Component;

public final class LevelCustomizer {

    private LevelCustomizer() {}
    private static final ComponentCache CACHE = new ComponentCache();
    private static final ComponentCache NAMETAG_CACHE = new ComponentCache();

    private static void dbg(String msg) {
        if (SkyCustomizerConfig.get().debugMode) {
            System.out.println("[SkyCustomizer|LevelCustomizer] " + msg);
        }
    }

    public static Component processComponent(Component original) {
        return processComponent(original, false);
    }

    public static Component processNametag(Component original) {
        return processComponent(original, true);
    }

    private static Component processComponent(Component original, boolean playerLabel) {
        if (original == null) return null;
        SkyCustomizerConfig cfg = SkyCustomizerConfig.get();
        if (!cfg.enabled || !cfg.level.enabled) return original;

        if (original.getString().indexOf('[') < 0) return original;
        long revision = SkyCustomizerConfig.runtimeRevision();
        long frame = cfg.level.chromaFor600Plus ? System.nanoTime() / 33_000_000L : 0;
        ComponentCache cache = playerLabel ? NAMETAG_CACHE : CACHE;
        Component cached = cache.get(original, revision, frame);
        if (cached != null) return cached;
        if (cfg.debugMode) dbg("processComponent: \"" + original.getString() + "\"");
        Component result = TextUtil.recolorLevelPrefix(original, playerLabel);
        if (result != original) {
            cache.put(original, result, revision, frame);
            if (cfg.debugMode) dbg("  → recolored: \"" + result.getString() + "\"");
        }
        return result;
    }

}
