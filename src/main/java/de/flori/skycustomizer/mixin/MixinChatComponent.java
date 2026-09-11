package de.flori.skycustomizer.mixin;

import de.flori.skycustomizer.config.SkyCustomizerConfig;
import de.flori.skycustomizer.features.ChatCustomizer;
import de.flori.skycustomizer.features.NameCustomizer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

@Mixin(ChatComponent.class)
public class MixinChatComponent {

    private static boolean skycustomizer$hookFiredOnce = false;

    private static Component skycustomizer$transform(Component original) {
        if (original == null) return null;
        try {
            SkyCustomizerConfig cfg = SkyCustomizerConfig.get();
            if (cfg.debugMode) {
                if (!skycustomizer$hookFiredOnce) {
                    skycustomizer$hookFiredOnce = true;
                    System.out.println("[SkyCustomizer][Chat] Hook active! First message: '"
                            + original.getString().substring(0, Math.min(80, original.getString().length())) + "'");
                }
                System.out.println("[SkyCustomizer][Chat] msg: '" + original.getString().substring(0, Math.min(60, original.getString().length()))
                        + "' enabled=" + cfg.enabled + " level=" + cfg.level.enabled);
            }
            if (!cfg.enabled) return original;

            Component result = original;

            if (cfg.name.enabled) {
                String username = Minecraft.getInstance().getUser() != null
                        ? Minecraft.getInstance().getUser().getName() : null;
                if (username != null && !username.isEmpty()) {
                    Component named = NameCustomizer.customizeChat(result, username);
                    if (named != result) result = named;
                }
            }

            Component processed = ChatCustomizer.processIncoming(result);
            return processed;
        } catch (Exception e) {
            return original;
        }
    }

    @WrapOperation(
        method = "addPlayerMessage",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/ChatComponent;addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V"
        )
    )
    private void skycustomizer$wrapPlayerMessage(
            ChatComponent instance, Component msg, MessageSignature sig,
            GuiMessageSource src, GuiMessageTag tag, Operation<Void> op) {
        skycustomizer$forward(instance, msg, sig, src, tag, op);
    }

    @WrapOperation(
        method = "addServerSystemMessage",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/ChatComponent;addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V"
        )
    )
    private void skycustomizer$wrapServerSystem(
            ChatComponent instance, Component msg, MessageSignature sig,
            GuiMessageSource src, GuiMessageTag tag, Operation<Void> op) {
        skycustomizer$forward(instance, msg, sig, src, tag, op);
    }

    @WrapOperation(
        method = "addClientSystemMessage",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/ChatComponent;addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V"
        )
    )
    private void skycustomizer$wrapClientSystem(
            ChatComponent instance, Component msg, MessageSignature sig,
            GuiMessageSource src, GuiMessageTag tag, Operation<Void> op) {
        skycustomizer$forward(instance, msg, sig, src, tag, op);
    }

    private static void skycustomizer$forward(
            ChatComponent instance, Component msg, MessageSignature sig,
            GuiMessageSource src, GuiMessageTag tag, Operation<Void> op) {
        Component transformed = skycustomizer$transform(msg);
        if (transformed != null) {
            op.call(instance, transformed, sig, src, tag);
        }
    }
}
