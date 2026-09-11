package de.flori.skycustomizer.util;

import de.flori.skycustomizer.config.SkyCustomizerConfig.NametagRule;
import de.flori.skycustomizer.features.NameCustomizer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import java.util.*;

public final class NametagRules {
    private NametagRules() {}
    public static NametagRule match(List<NametagRule> rules,String username,boolean self,int level) {
        if(rules==null)return null;
        for(NametagRule rule:rules){if(rule==null||!rule.enabled)continue;
            boolean matches=switch(Objects.toString(rule.target,"")){
                case "ANY" -> true;case "SELF" -> self;
                case "PLAYER" -> username.equalsIgnoreCase(Objects.toString(rule.username,""));
                case "LEVEL" -> level>=0&&level>=rule.minLevel&&level<=rule.maxLevel;
                default -> false;
            };if(matches)return rule;
        }return null;
    }
    public static int level(Component original){
        var matcher=TextUtil.LEVEL_PATTERN.matcher(TextUtil.stripLegacyFormatting(original.getString()));
        if(!matcher.find())return -1;
        try{return Integer.parseInt(matcher.group(1));}catch(NumberFormatException e){return -1;}
    }
    public static Component apply(Component original,String username,NametagRule rule){
        return apply(original, username, Component.literal(username), rule);
    }
    public static Component apply(Component original, String username, Component displayName, NametagRule rule) {
        Style ruleStyle = Style.EMPTY.withColor(ColorUtil.fromHex(rule.colorHex) & 0xFFFFFF)
                .withBold(rule.bold).withItalic(rule.italic);
        var name = TextUtil.parseLegacyFormatting(Objects.toString(rule.prefix, ""), ruleStyle);
        displayName.visit((style, text) -> {
            name.append(Component.literal(text).withStyle(ruleStyle.applyTo(style)));
            return Optional.empty();
        }, Style.EMPTY);
        name.append(TextUtil.parseLegacyFormatting(Objects.toString(rule.suffix, ""), ruleStyle));
        return NameCustomizer.replaceUsername(original, username, name, false);
    }
}
