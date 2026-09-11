package de.flori.skycustomizer.util;

import de.flori.skycustomizer.config.SkyCustomizerConfig;
import de.flori.skycustomizer.features.NameCustomizer;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;

import java.util.Optional;

final class NameReplacementChecks {
    private static int assertions;

    static void run() {
        var replacement = TextUtil.parseLegacyFormatting("&b&lNova", Style.EMPTY);
        var chat = Component.literal("Alex: hi Alex, @alex! ALEX? Alexander Alex_2 xAlex Alex2 Alex.");
        var all = NameCustomizer.replaceUsername(chat, "Alex", replacement, true);
        check(all.getString().equals("Nova: hi Nova, @Nova! Nova? Alexander Alex_2 xAlex Alex2 Nova."), "Every complete mention, case insensitive");
        check(NameCustomizer.replaceUsername(chat, "Alex", replacement, false).getString()
                .equals("Nova: hi Alex, @alex! ALEX? Alexander Alex_2 xAlex Alex2 Alex."), "Single-name labels only replace their first name");
        check(chat.getString().startsWith("Alex:"), "Incoming component is not mutated");
        check(NameCustomizer.replaceUsername(chat, "", replacement, true) == chat, "Empty name is ignored");
        check(NameCustomizer.replaceUsername(chat, "Nobody", replacement, true) == chat, "Unmatched component reused");
        check(NameCustomizer.replaceUsername(Component.literal("Alex Alex"), "Alex", Component.literal("Alex X"), true)
                .getString().equals("Alex X Alex X"), "Replacement text is never processed recursively");
        check(NameCustomizer.replaceUsername(Component.literal("Alex Alex"), "Alex", Component.empty(), true)
                .getString().equals(" "), "Empty replacement is safe");
        check(NameCustomizer.usernameStart("äAlex Alexé Alex_", "Alex") == -1, "Unicode and underscore boundaries");

        var click = new ClickEvent.SuggestCommand("/msg Alex ");
        var hover = new HoverEvent.ShowText(Component.literal("Real player: Alex"));
        Style sourceStyle = Style.EMPTY.withColor(0x123456).withClickEvent(click).withHoverEvent(hover).withInsertion("Alex");
        String text = "[MVP+] Alex: @Alex and ALEX.";
        for (int split = 0; split <= text.length(); split++) {
            Component source = Component.literal(text.substring(0, split)).withStyle(sourceStyle)
                    .append(Component.literal(text.substring(split)).withStyle(sourceStyle.withItalic(true)));
            Component result = NameCustomizer.replaceUsername(source, "Alex", replacement, true);
            check(result.getString().equals("[MVP+] Nova: @Nova and Nova."), "Names spanning component boundaries, split " + split);
            Style alias = styleAt(result, 7);
            check(alias.isBold() && alias.getColor().getValue() == 0x55FFFF, "Custom name styles survive, split " + split);
            check(click.equals(alias.getClickEvent()) && hover.equals(alias.getHoverEvent()) && "Alex".equals(alias.getInsertion()),
                    "Real-player click/hover/insertion retained, split " + split);
            check(styleAt(result, 0).getColor().getValue() == 0x123456, "Unchanged rank retains color");
        }
        Component dense = Component.empty();
        for (char c : "Alex Alex".toCharArray()) dense.getSiblings().add(Component.literal(String.valueOf(c)));
        check(NameCustomizer.replaceUsername(dense, "Alex", replacement, true).getString().equals("Nova Nova"), "Names across per-character components");
        Component many = NameCustomizer.replaceUsername(Component.literal("Alex ".repeat(1000)), "Alex", replacement, true);
        check(many.getString().equals("Nova ".repeat(1000)) && many.getSiblings().size() <= 2000, "Many mentions remain linear in output segments");

        Component legacy = TextUtil.parseSectionFormatting(Component.literal("§aAlex §7[§b120§7] R&D &a").withStyle(sourceStyle));
        check(legacy.getString().equals("Alex [120] R&D &a"), "Raw section codes disappear, ordinary ampersands remain");
        check(styleAt(legacy, 0).getColor().getValue() == 0x55FF55 && click.equals(styleAt(legacy, 0).getClickEvent()), "Legacy parsing preserves interaction");
        check(TextUtil.parseSectionFormatting(chat) == chat, "Styled components require no reparsing");

        var rule = new SkyCustomizerConfig.NametagRule();
        rule.prefix = "[Friend] "; rule.colorHex = "#FF8800"; rule.bold = true;
        var tag = NametagRules.apply(Component.literal("[120] Alex"), "Alex", replacement, rule);
        check(tag.getString().equals("[120] [Friend] Nova"), "Nametag rule retains custom display name and server badge");
        check(styleAt(tag, 15).getColor().getValue() == 0xFF8800 && styleAt(tag, 15).isBold(), "Explicit rule style applies to alias");
        System.out.println("Name replacements: " + assertions + " checks passed.");
    }

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
}
