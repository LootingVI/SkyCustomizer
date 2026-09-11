package de.flori.skycustomizer.mixin;

import de.flori.skycustomizer.config.SkyCustomizerConfig;
import de.flori.skycustomizer.features.LevelCustomizer;
import de.flori.skycustomizer.features.NameCustomizer;
import de.flori.skycustomizer.util.TextUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public abstract class MixinLivingEntityRenderer {
    @Inject(method = "createRenderState(Lnet/minecraft/world/entity/Entity;F)Lnet/minecraft/client/renderer/entity/state/EntityRenderState;",
            at = @At("RETURN"))
    private void skycustomizer$nametagState(Entity entity, float partialTick, CallbackInfoReturnable<EntityRenderState> cir) {
        var cfg = SkyCustomizerConfig.get();
        EntityRenderState state = cir.getReturnValue();
        if (!cfg.enabled || state == null) return;
        Component original = state.nameTag;
        if (original != null) {
            Minecraft mc = Minecraft.getInstance();
            String username = mc.getUser() == null ? "" : mc.getUser().getName();
            if (entity instanceof Player player) {
                state.nameTag = NameCustomizer.customizePlayerNametag(original, player.getScoreboardName(),
                        mc.getUser() != null && player.getUUID().equals(mc.getUser().getProfileId()));
            } else if (NameCustomizer.usernameStart(TextUtil.stripLegacyFormatting(original.getString()), username) >= 0) {
                state.nameTag = NameCustomizer.customizeNametag(original, username);
            } else {
                state.nameTag = LevelCustomizer.processComponent(original);
            }
        }
        if (!cfg.name.enabled || !(entity instanceof Player)) return;
        var n = cfg.name;
        float distance = Float.isFinite(n.nametagMaxDistance) ? Math.clamp(n.nametagMaxDistance, 8, 64) : 64;
        if (n.hidePlayerNametags || state.nameTag == null || state.distanceToCameraSq > distance * distance) {
            state.nameTag = null;
            state.scoreText = null;
            return;
        }
        if (n.hideNametagScores) state.scoreText = null;
        if (state.nameTagAttachment != null && Float.isFinite(n.nametagOffsetY) && n.nametagOffsetY != 0) {
            state.nameTagAttachment = state.nameTagAttachment.add(0, Math.clamp(n.nametagOffsetY, -1, 2), 0);
        }
    }
}
