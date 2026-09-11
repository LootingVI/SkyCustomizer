package de.flori.skycustomizer.mixin;

import de.flori.skycustomizer.config.SkyCustomizerConfig;
import de.flori.skycustomizer.features.LevelCustomizer;
import de.flori.skycustomizer.features.NameCustomizer;
import de.flori.skycustomizer.util.TextUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerTabOverlay.class)
public abstract class MixinPlayerTabOverlay {

    private static void dbg(String msg) {
        if (SkyCustomizerConfig.get().debugMode) {
            System.out.println("[SkyCustomizer|Tab] " + msg);
        }
    }

    @Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true)
    private void skycustomizer$modifyTabName(
            PlayerInfo info, CallbackInfoReturnable<Component> cir) {

        Component original = cir.getReturnValue();
        if (original == null) return;

        try {
            SkyCustomizerConfig cfg = SkyCustomizerConfig.get();
            if (!cfg.enabled) return;

            String displayText = stripSection(original.getString());
            if (cfg.debugMode) dbg("tab entry: \"" + displayText + "\"");

            if (!TextUtil.LEVEL_PATTERN.matcher(displayText).find()) {
                if (info != null && Minecraft.getInstance().level != null) {
                    try {
                        var scoreboard = Minecraft.getInstance().level.getScoreboard();
                        if (scoreboard != null && info.getProfile() != null) {
                            var team = scoreboard.getPlayersTeam(info.getProfile().name());
                            if (team != null) {
                                String prefixSuffix = stripSection(
                                        team.getPlayerPrefix().getString()
                                        + team.getPlayerSuffix().getString());
                                if (cfg.debugMode) dbg("team prefix+suffix: \"" + prefixSuffix + "\"");
                                if (TextUtil.LEVEL_PATTERN.matcher(prefixSuffix).find()) {

                                    original = Component.literal(prefixSuffix + " ")
                                            .append(original);
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                }
            }

            Component result = LevelCustomizer.processNametag(original);

            if (cfg.name.enabled && cfg.name.applyInTab) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.getUser() != null) {
                    String username = mc.getUser().getName();
                    if (username != null && !username.isEmpty()
                            && NameCustomizer.usernameStart(result.getString(), username) >= 0) {
                        result = NameCustomizer.customizeTabName(result, username);
                    }
                }
            }

            if (result != original) {
                if (cfg.debugMode) dbg("  → modified to: \"" + result.getString() + "\"");
                cir.setReturnValue(result);
            }

        } catch (Exception e) {
            dbg("error: " + e.getMessage());
        }
    }

    private static String stripSection(String s) {
        if (s == null) return "";
        return TextUtil.stripLegacyFormatting(s).trim();
    }
}
