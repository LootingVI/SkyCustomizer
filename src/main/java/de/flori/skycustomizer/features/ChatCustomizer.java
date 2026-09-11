package de.flori.skycustomizer.features;

import de.flori.skycustomizer.config.SkyCustomizerConfig;
import de.flori.skycustomizer.util.ColorUtil;
import de.flori.skycustomizer.util.RegexCache;
import de.flori.skycustomizer.util.TextUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.sounds.SoundEvents;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public final class ChatCustomizer {
    private ChatCustomizer() {}
    private static String lastMessage = "";
    private static long lastNotification;
    private static Component latestMessage = Component.empty();
    private static String timestampPattern;
    private static DateTimeFormatter timestampFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");

    public static Component processIncoming(Component original) {
        var cfg = SkyCustomizerConfig.get();
        if (!cfg.enabled) return original;
        var chat = cfg.chat;
        if (!chat.enabled) return chat.levelColorInChat ? LevelCustomizer.processComponent(original) : original;
        String text = original.getString();
        for (String hide : chat.hideMessageContains)
            if (hide != null && !hide.isEmpty() && text.contains(hide)) return null;
        for (String regex : chat.hideMessageRegex) {
            var pattern = RegexCache.compile(regex);
            if (regex != null && !regex.isEmpty() && pattern != null && pattern.matcher(text).find()) return null;
        }

        Component result = original;
        boolean keywordMatch = false;
        if (chat.recolorChatTypes) {
            for (var entry : chat.chatTypeColors.entrySet()) {
                String tag = "[" + entry.getKey() + "]";
                int start = text.indexOf(tag);
                if (start < 0) { tag = entry.getKey() + " >"; start = text.indexOf(tag); }
                if (start >= 0) {
                    result = TextUtil.recolorRange(result, start, start + tag.length(), ColorUtil.fromHex(entry.getValue()) & 0xFFFFFF);
                    break;
                }
            }
        }
        for (var entry : chat.highlightWords.entrySet()) {
            String word = entry.getKey();
            if (word == null || word.isEmpty()) continue;
            for (int start = text.indexOf(word); start >= 0; start = text.indexOf(word, start + word.length())) {
                result = TextUtil.recolorRange(result, start, start + word.length(), ColorUtil.fromHex(entry.getValue()) & 0xFFFFFF);
                keywordMatch = true;
            }
        }
        String username = Minecraft.getInstance().getUser() == null ? "" : Minecraft.getInstance().getUser().getName();
        boolean mention = !username.isBlank() && text.toLowerCase(java.util.Locale.ROOT)
                .contains(username.toLowerCase(java.util.Locale.ROOT));
        if ((chat.mentionSound && mention) || (chat.keywordSound && keywordMatch)) playNotification();
        if (chat.levelColorInChat) result = LevelCustomizer.processComponent(result);
        if (chat.compactChat && chat.duplicateGrouping) {
            String current = result.getString();
            if (current.equals(lastMessage)) return null;
            lastMessage = current;
        } else lastMessage = "";

        if (chat.timestampEnabled) {
            if (!java.util.Objects.equals(timestampPattern, chat.timestampFormat)) {
                timestampPattern = chat.timestampFormat;
                try { timestampFormatter = DateTimeFormatter.ofPattern(timestampPattern); }
                catch (IllegalArgumentException | NullPointerException ignored) { timestampFormatter = DateTimeFormatter.ofPattern("HH:mm:ss"); }
            }
            String time = LocalTime.now().format(timestampFormatter);
            result = Component.literal("[" + time + "] ")
                    .withStyle(Style.EMPTY.withColor(ColorUtil.fromHex(chat.timestampColorHex) & 0xFFFFFF))
                    .append(result);
        }
        if (chat.chatAccentBar || "ACCENT".equalsIgnoreCase(chat.chatTheme)) {
            result = Component.literal("▎ ").withStyle(Style.EMPTY.withColor(ColorUtil.fromHex(chat.chatAccentHex) & 0xFFFFFF))
                    .append(result);
        }
        latestMessage = result.copy();
        return result;
    }

    public static boolean copyLatestMessage() {
        if (latestMessage == null || latestMessage.getString().isBlank()) return false;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.keyboardHandler == null) return false;
        minecraft.keyboardHandler.setClipboard(latestMessage.getString());
        return true;
    }

    private static void playNotification() {
        long now = System.nanoTime();
        if (now - lastNotification < 500_000_000L) return;
        lastNotification = now;
        var player = Minecraft.getInstance().player;
        if (player != null) player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), .45f, 1.35f);
    }
}
