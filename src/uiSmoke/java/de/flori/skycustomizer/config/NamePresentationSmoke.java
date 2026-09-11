package de.flori.skycustomizer.config;

import de.flori.skycustomizer.features.ChatCustomizer;
import de.flori.skycustomizer.features.LevelCustomizer;
import de.flori.skycustomizer.features.NameCustomizer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.Style;
import net.minecraft.world.entity.Entity;

import java.util.List;
import java.util.Optional;
import java.util.TreeMap;

final class NamePresentationSmoke {
    private static int assertions;

    static void run(Minecraft mc) throws Exception {
        var cfg = SkyCustomizerConfig.get();
        var savedName = cfg.name;
        var savedLevel = cfg.level;
        var savedChat = cfg.chat;
        boolean savedEnabled = cfg.enabled;
        try {
            cfg.enabled = true;
            cfg.name = new SkyCustomizerConfig.NameConfig();
            cfg.level = new SkyCustomizerConfig.LevelConfig();
            cfg.chat = new SkyCustomizerConfig.ChatConfig();
            cfg.name.useCustomName = true;
            cfg.name.customName = "&#12AB34&lNova";
            cfg.level.chromaFor600Plus = false;
            cfg.level.customColors = new TreeMap<>();
            cfg.level.customColors.put(0, "#112233");
            cfg.level.bracketColorHex = "#556677";
            cfg.chat.timestampEnabled = false;
            revision();
            String username = mc.getUser().getName();
            Component source = Component.literal("[MVP+] [120] " + username).withColor(0xAA55FF);
            Component tag = NameCustomizer.customizePlayerNametag(source, username, true);
            check(tag.getString().equals("[MVP+] [120] Nova"), "Custom name reaches overhead label");
            check(color(tag, 8) == 0x112233 && color(tag, 7) == 0x556677, "Custom level and bracket after rank prefix");
            check(color(tag, 13) == 0x12AB34 && styleAt(tag, 13).isBold(), "Name formatting reaches nametag");
            check(color(tag, 0) == 0xAA55FF, "Rank color preserved");
            Component legacy = NameCustomizer.customizePlayerNametag(Component.literal("§a[MVP+] §7[§b120§7] §a" + username), username, true);
            check(legacy.getString().equals("[MVP+] [120] Nova") && color(legacy, 8) == 0x112233, "Legacy nametag uses alias and level without code letters");

            cfg.name.enabled = false; revision();
            tag = NameCustomizer.customizePlayerNametag(source, username, true);
            check(tag.getString().equals(source.getString()) && color(tag, 8) == 0x112233, "Level colors independent of name toggle");
            cfg.name.enabled = true; cfg.name.applyOverHead = false; revision();
            tag = NameCustomizer.customizePlayerNametag(source, username, true);
            check(tag.getString().equals(source.getString()) && color(tag, 8) == 0x112233, "Overhead toggle only disables name change");
            cfg.name.applyOverHead = true; cfg.level.enabled = false; revision();
            tag = NameCustomizer.customizePlayerNametag(source, username, true);
            check(tag.getString().endsWith("Nova") && color(tag, 8) == 0xAA55FF, "Name independent of level toggle");
            cfg.level.enabled = true; revision();

            var rule = new SkyCustomizerConfig.NametagRule();
            rule.target = "SELF"; rule.prefix = "[Friend] "; rule.colorHex = "#FF8800";
            cfg.name.rules.add(rule); revision();
            tag = NameCustomizer.customizePlayerNametag(source, username, true);
            check(tag.getString().equals("[MVP+] [120] [Friend] Nova") && color(tag, 8) == 0x112233, "Rules retain alias and level color");
            rule.hidden = true; revision();
            check(NameCustomizer.customizePlayerNametag(source, username, true) == null, "Hidden rule remains hidden");
            cfg.name.rules.clear(); cfg.name.playerAliases.put("otherplayer", "&eCompanion"); revision();
            check(NameCustomizer.customizePlayerNametag(Component.literal("[120] OtherPlayer"), "OtherPlayer", false)
                    .getString().equals("[120] Companion"), "Other-player alias is case insensitive");
            Component untouched = Component.literal("UnconfiguredPlayer");
            check(NameCustomizer.customizePlayerNametag(untouched, "UnconfiguredPlayer", false) == untouched,
                    "Unconfigured players avoid name rebuilding on each frame");
            check(NameCustomizer.customizePlayerNametag(null, username, true) == null, "Vanilla-hidden tag is never revealed");

            Component message = Component.literal("Someone: @" + username + ", " + username.toUpperCase(java.util.Locale.ROOT) + "!");
            String expected = "Someone: @Nova, Nova!";
            check(NameCustomizer.customizeChat(message, username).getString().equals(expected), "Repeated mentions in another sender's message");
            cfg.name.applyInChat = false; revision();
            check(NameCustomizer.customizeChat(message, username) == message, "Chat name toggle honored");
            cfg.name.applyInChat = true; cfg.chat.levelColorInChat = false; revision();
            Component chatBadge = Component.literal("[120] " + username + ": hello").withColor(0xAA55FF);
            Component result = ChatCustomizer.processIncoming(NameCustomizer.customizeChat(chatBadge, username));
            check(result.getString().equals("[120] Nova: hello") && color(result, 1) == 0xAA55FF, "Chat level switch is not overridden by alias processing");
            check(LevelCustomizer.processComponent(Component.literal("Items [120]"))
                    .equals(Component.literal("Items [120]")), "Generic message numbers are not treated as player levels");

            ChatComponent chat = new ChatComponent(mc);
            var messagesField = ChatComponent.class.getDeclaredField("allMessages");
            messagesField.setAccessible(true);
            chat.addServerSystemMessage(message);
            check(latest(chat, messagesField).content().getString().equals(expected), "Server chat entry hook");
            chat.addClientSystemMessage(message);
            check(latest(chat, messagesField).content().getString().equals(expected), "Client chat entry hook");
            MessageSignature signature = new MessageSignature(new byte[256]);
            chat.addPlayerMessage(message, signature, null);
            check(latest(chat, messagesField).content().getString().equals(expected), "Player chat entry hook");
            check(latest(chat, messagesField).signature() == signature, "Chat signature metadata retained");
            int before = ((List<?>) messagesField.get(chat)).size();
            cfg.chat.hideMessageContains.add("Blocked");
            chat.addServerSystemMessage(Component.literal("Blocked " + username));
            check(((List<?>) messagesField.get(chat)).size() == before, "Chat filters still cancel messages");

            var probe = new RendererProbe(mc, source);
            tag = probe.createRenderState(null, 0).nameTag;
            check(tag.getString().equals("[MVP+] [120] Nova") && color(tag, 8) == 0x112233, "Injected render-state hook runs after renderer overrides");
            check(new RendererProbe(mc, null).createRenderState(null, 0).nameTag == null, "Render hook preserves hidden state");
            cfg.enabled = false; revision();
            check(NameCustomizer.customizePlayerNametag(source, username, true) == source
                    && NameCustomizer.customizeChat(message, username) == message, "Master switch honored");
            System.out.println("[SkyCustomizer] NAME_PRESENTATION_PASSED: " + assertions + " checks");
        } finally {
            cfg.name = savedName; cfg.level = savedLevel; cfg.chat = savedChat; cfg.enabled = savedEnabled;
            revision();
        }
    }

    private static GuiMessage latest(ChatComponent chat, java.lang.reflect.Field field) throws IllegalAccessException {
        return (GuiMessage) ((List<?>) field.get(chat)).getFirst();
    }

    private static void revision() throws ReflectiveOperationException {
        var field = SkyCustomizerConfig.class.getDeclaredField("runtimeRevision");
        field.setAccessible(true); field.setLong(null, field.getLong(null) + 1);
    }

    private static int color(Component component, int position) { return styleAt(component, position).getColor().getValue(); }

    private static Style styleAt(Component component, int position) {
        int[] index = {0};
        return component.visit((style, text) -> {
            int start = index[0]; index[0] += text.length();
            return position >= start && position < index[0] ? Optional.of(style) : Optional.<Style>empty();
        }, Style.EMPTY).orElseThrow();
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private static final class RendererProbe extends EntityRenderer<Entity, EntityRenderState> {
        private final Component label;
        RendererProbe(Minecraft mc, Component label) {
            super(new EntityRendererProvider.Context(mc.getEntityRenderDispatcher(), null, null, null, null,
                    null, null, mc.getAtlasManager(), mc.font, mc.playerSkinRenderCache()));
            this.label = label;
        }
        @Override public EntityRenderState createRenderState() { return new EntityRenderState(); }
        @Override public void extractRenderState(Entity entity, EntityRenderState state, float partialTick) { state.nameTag = label; }
        @Override protected void finalizeRenderState(Entity entity, EntityRenderState state) {}
    }
}
