package de.flori.skycustomizer.features.visuals;

import de.flori.skycustomizer.config.SkyCustomizerConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public final class HudCustomizer {
    private static Component lastMessage;
    private static Object messageLevel;
    private static long messageTime;
    private static final Component SAMPLE = Component.empty()
            .append(Component.literal("1,250/1,250 HP   ").withColor(0xFF5555))
            .append(Component.literal("350 Defense   ").withColor(0x55FF55))
            .append(Component.literal("420/420 Mana").withColor(0x55FFFF));

    private HudCustomizer() {}
    public static boolean isCustomHudEnabled() {
        var c = SkyCustomizerConfig.get().visuals;
        return SkyCustomizerConfig.get().enabled && c.enabled && c.customHud;
    }

    public static void rememberMessage(Component message) {
        lastMessage = message;
        messageLevel = Minecraft.getInstance().level;
        messageTime = System.nanoTime();
    }

    public static Component previewMessage() {
        return messageLevel == Minecraft.getInstance().level && lastMessage != null
                && !lastMessage.getString().isEmpty() && System.nanoTime() - messageTime < 3_000_000_000L
                ? lastMessage : SAMPLE;
    }

    public static void drawPreview(GuiGraphicsExtractor graphics, Component message, float x, float y, float scale) {
        var font = Minecraft.getInstance().font;
        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate(x, y);
            graphics.pose().scale(scale, scale);
            graphics.textWithBackdrop(font, message, 0, 0, font.width(message), -1);
        } finally {
            graphics.pose().popMatrix();
        }
    }
}
