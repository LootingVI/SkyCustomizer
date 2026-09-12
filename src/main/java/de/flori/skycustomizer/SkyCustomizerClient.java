package de.flori.skycustomizer;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import de.flori.skycustomizer.config.SkyCustomizerConfig;
import de.flori.skycustomizer.config.SkyCustomizerConfigScreen;
import de.flori.skycustomizer.features.visuals.PerformanceHud;
import de.flori.skycustomizer.features.DiscordRichPresence;
import de.flori.skycustomizer.util.ItemIdentity;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class SkyCustomizerClient implements ClientModInitializer {

    private static final String MOD_ID = "skycustomizer";

    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "keys"));

    private static KeyMapping openConfigKey;

    @Override
    public void onInitializeClient() {
        SkyCustomizerConfig cfg = SkyCustomizerConfig.get();

        log("Loaded config v" + cfg.configVersion
                + " - " + cfg.level.customColors.size() + " level thresholds");
        log("GUI: modern custom (Sodium/Reese's inspired) - ModMenu -> SkyCustomizer");

        registerKeybind();
        registerCommands(cfg);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> DiscordRichPresence.shutdown());

        log("Initialized - Fabric 26.1.2");
    }

    private static void log(String msg) {
        System.out.println("[SkyCustomizer] " + msg);
    }

    private void registerKeybind() {
        openConfigKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.skycustomizer.openConfig",
                InputConstants.Type.KEYSYM,
                InputConstants.KEY_K,
                CATEGORY
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            PerformanceHud.tick();
            DiscordRichPresence.tick();
            if (openConfigKey != null && openConfigKey.consumeClick() && client.screen == null) {
                openConfigScreen(null);
            }
        });

        log("Keybind K registered");
    }

    public static void openConfigScreen(Screen parent) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            mc.setScreen(new SkyCustomizerConfigScreen(parent));
        }
    }

    private static String parseHex(String raw, FabricClientCommandSource src) {
        String hex = raw.startsWith("#") ? raw : "#" + raw;
        if (!hex.matches("#[0-9A-Fa-f]{6}")) {
            src.sendError(Component.literal("§cInvalid hex: " + hex + " §7(use #RRGGBB)"));
            return null;
        }
        return hex.toUpperCase();
    }

    private static String argString(com.mojang.brigadier.context.CommandContext<FabricClientCommandSource> ctx, String name) {
        return StringArgumentType.getString(ctx, name);
    }

    private void registerCommands(SkyCustomizerConfig cfg) {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> {

            var root = dispatcher.register(
                    ClientCommands.literal("skycustomizer")
                            .executes(ctx -> { openConfigScreen(null); return 1; })
                            .then(ClientCommands.literal("config")
                                    .executes(ctx -> { openConfigScreen(null); return 1; }))
                            .then(ClientCommands.literal("reload")
                                    .executes(ctx -> {
                                        SkyCustomizerConfig.load();
                                        ctx.getSource().sendFeedback(
                                                Component.literal("§aSkyCustomizer reloaded."));
                                        return 1;
                                    }))
                            .then(buildPresetCommand(cfg))
                            .then(buildLevelCommand(cfg))
                            .then(buildLevelColorCommand(cfg))
                            .then(buildArmorCommand(cfg))
                            .then(buildNameCommand(cfg))
                            .then(buildChatCommand(cfg))
                            .then(buildScoreboardCommand(cfg))
                            .then(buildItemsCommand(cfg))
                            .then(buildHeldItemCommand("itemname", false))
                            .then(buildHeldItemCommand("itemlore", true))
                            .then(ClientCommands.literal("itemreset").executes(ctx -> resetHeldItem(ctx.getSource())))
                            .then(buildVisualsCommand(cfg))
                            .then(ClientCommands.literal("enabled")
                                    .then(ClientCommands.literal("on").executes(ctx -> {
                                        SkyCustomizerConfig live = SkyCustomizerConfig.get();
                                        live.enabled = true; live.save();
                                        ctx.getSource().sendFeedback(
                                                Component.literal("§aSkyCustomizer globally ENABLED"));
                                        return 1;
                                    }))
                                    .then(ClientCommands.literal("off").executes(ctx -> {
                                        SkyCustomizerConfig live = SkyCustomizerConfig.get();
                                        live.enabled = false; live.save();
                                        ctx.getSource().sendFeedback(
                                                Component.literal("§cSkyCustomizer globally DISABLED"));
                                        return 1;
                                    }))
                                    .executes(ctx -> {
                                        SkyCustomizerConfig live = SkyCustomizerConfig.get();
                                        live.enabled = !live.enabled; live.save();
                                        ctx.getSource().sendFeedback(
                                                Component.literal(live.enabled
                                                        ? "§aSkyCustomizer globally ENABLED"
                                                        : "§cSkyCustomizer globally DISABLED"));
                                        return 1;
                                    }))
                            .then(ClientCommands.literal("debug")
                                    .then(ClientCommands.literal("on").executes(ctx -> {
                                        SkyCustomizerConfig live = SkyCustomizerConfig.get();
                                        live.debugMode = true; live.save();
                                        ctx.getSource().sendFeedback(
                                                Component.literal("§aSkyCustomizer debugMode ON — check game console/logs for [SkyCustomizer][Chat] hook output"));
                                        ctx.getSource().sendFeedback(
                                                Component.literal("§7enabled=" + live.enabled
                                                        + " level.enabled=" + live.level.enabled
                                                        + " chat.levelColor=" + live.chat.levelColorInChat));
                                        return 1;
                                    }))
                                    .then(ClientCommands.literal("off").executes(ctx -> {
                                        SkyCustomizerConfig live = SkyCustomizerConfig.get();
                                        live.debugMode = false; live.save();
                                        ctx.getSource().sendFeedback(
                                                Component.literal("§cSkyCustomizer debugMode OFF"));
                                        return 1;
                                    }))
                                    .executes(ctx -> {
                                        SkyCustomizerConfig live = SkyCustomizerConfig.get();
                                        live.debugMode = !live.debugMode; live.save();
                                        ctx.getSource().sendFeedback(
                                                Component.literal(live.debugMode
                                                        ? "§aSkyCustomizer debugMode ON — check game console/logs for [SkyCustomizer][Chat] hook output"
                                                        : "§cSkyCustomizer debugMode OFF"));
                                        ctx.getSource().sendFeedback(
                                                Component.literal("§7enabled=" + live.enabled
                                                        + " level.enabled=" + live.level.enabled
                                                        + " chat.levelColor=" + live.chat.levelColorInChat));
                                        return 1;
                                    }))
            );

            dispatcher.register(ClientCommands.literal("sc")
                    .executes(ctx -> { openConfigScreen(null); return 1; })
                    .redirect(root));
        });

        log("Commands registered: /skycustomizer, /sc");
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> boolToggle(
            String name, java.util.function.Consumer<Boolean> setter, String onMsg, String offMsg) {
        return ClientCommands.literal(name)
                .then(ClientCommands.literal("true").executes(ctx -> {
                    setter.accept(true);
                    ctx.getSource().sendFeedback(Component.literal(onMsg)); return 1;
                }))
                .then(ClientCommands.literal("false").executes(ctx -> {
                    setter.accept(false);
                    ctx.getSource().sendFeedback(Component.literal(offMsg)); return 1;
                }));
    }

    private LiteralArgumentBuilder<FabricClientCommandSource> buildPresetCommand(SkyCustomizerConfig cfg) {
        return ClientCommands.literal("preset")
                .then(ClientCommands.argument("name", StringArgumentType.word())
                        .then(ClientCommands.literal("save").executes(ctx -> {
                            String name = argString(ctx, "name");
                            try {
                                cfg.savePreset(name);
                                ctx.getSource().sendFeedback(Component.literal("§aPreset saved: " + name));
                                return 1;
                            } catch (Exception e) {
                                ctx.getSource().sendError(Component.literal("§cCould not save preset: " + e.getMessage()));
                                return 0;
                            }
                        }))
                        .then(ClientCommands.literal("load").executes(ctx -> {
                            String name = argString(ctx, "name");
                            try {
                                if (cfg.loadPreset(name)) {
                                    ctx.getSource().sendFeedback(Component.literal("§aPreset loaded: " + name));
                                    return 1;
                                }
                                ctx.getSource().sendError(Component.literal("§cPreset not found: " + name));
                                return 0;
                            } catch (Exception e) {
                                ctx.getSource().sendError(Component.literal("§cCould not load preset: " + e.getMessage()));
                                return 0;
                            }
                        })));
    }

    private LiteralArgumentBuilder<FabricClientCommandSource> buildLevelCommand(SkyCustomizerConfig cfg) {
        return ClientCommands.literal("level")
                .then(boolToggle("enabled",
                        b -> { cfg.level.enabled = b; cfg.save(); },
                        "§aLevel colors ON", "§cLevel colors OFF"))
                .then(boolToggle("override",
                        b -> { cfg.level.overrideHypixelColors = b; cfg.save(); },
                        "§aOverride 0-480 ON", "§cOverride 0-480 OFF"))
                .then(boolToggle("chroma",
                        b -> { cfg.level.chromaFor600Plus = b; cfg.save(); },
                        "§aChroma 600+ ON", "§cChroma 600+ OFF"))
                .then(ClientCommands.literal("speed")
                        .then(ClientCommands.argument("value", StringArgumentType.word())
                                .executes(ctx -> {
                                    String v = argString(ctx, "value");
                                    try {
                                        cfg.level.chromaSpeed = Float.parseFloat(v); cfg.save();
                                        ctx.getSource().sendFeedback(
                                                Component.literal("§aChroma speed: " + cfg.level.chromaSpeed));
                                        return 1;
                                    } catch (NumberFormatException e) {
                                        ctx.getSource().sendError(Component.literal("§cInvalid: " + v)); return 0;
                                    }
                                })))
                .then(ClientCommands.literal("bracketcolor")
                        .then(ClientCommands.argument("hex", StringArgumentType.word())
                                .executes(ctx -> {
                                    String hex = parseHex(argString(ctx, "hex"), ctx.getSource());
                                    if (hex == null) return 0;
                                    cfg.level.bracketColorHex = hex; cfg.save();
                                    ctx.getSource().sendFeedback(Component.literal("§aBracket color: " + hex));
                                    return 1;
                                })));
    }

    private LiteralArgumentBuilder<FabricClientCommandSource> buildLevelColorCommand(SkyCustomizerConfig cfg) {
        return ClientCommands.literal("levelcolor")
                .then(ClientCommands.argument("level", StringArgumentType.word())
                        .then(ClientCommands.argument("hex", StringArgumentType.word())
                                .executes(ctx -> {
                                    int lvl;
                                    try {
                                        lvl = Integer.parseInt(argString(ctx, "level"));
                                    } catch (NumberFormatException e) {
                                        ctx.getSource().sendError(Component.literal("§cLevel must be a number"));
                                        return 0;
                                    }
                                    String hex = parseHex(argString(ctx, "hex"), ctx.getSource());
                                    if (hex == null) return 0;
                                    cfg.level.customColors.put(lvl, hex); cfg.save();
                                    ctx.getSource().sendFeedback(
                                            Component.literal("§aLevel " + lvl + " -> " + hex));
                                    return 1;
                                })));
    }

    private LiteralArgumentBuilder<FabricClientCommandSource> buildArmorCommand(SkyCustomizerConfig cfg) {
        return ClientCommands.literal("armor")
                .then(boolToggle("enabled",
                        b -> { cfg.armor.enabled = b; cfg.save(); },
                        "§aArmor colors ON", "§cArmor colors OFF"))
                .then(boolToggle("chroma",
                        b -> { cfg.armor.chroma = b; cfg.save(); },
                        "§aArmor chroma ON", "§cArmor chroma OFF"))
                .then(ClientCommands.literal("speed")
                        .then(ClientCommands.argument("value", StringArgumentType.word())
                                .executes(ctx -> {
                                    String v = argString(ctx, "value");
                                    try {
                                        cfg.armor.chromaSpeed = Float.parseFloat(v); cfg.save();
                                        ctx.getSource().sendFeedback(
                                                Component.literal("§aArmor speed: " + cfg.armor.chromaSpeed));
                                        return 1;
                                    } catch (NumberFormatException e) {
                                        ctx.getSource().sendError(Component.literal("§cInvalid: " + v)); return 0;
                                    }
                                })))
                .then(buildArmorPiece("helmet", cfg))
                .then(buildArmorPiece("chest", cfg))
                .then(buildArmorPiece("legs", cfg))
                .then(buildArmorPiece("boots", cfg))
                .then(buildArmorPiece("global", cfg));
    }

    private LiteralArgumentBuilder<FabricClientCommandSource> buildArmorPiece(String piece, SkyCustomizerConfig cfg) {
        return ClientCommands.literal(piece)
                .then(ClientCommands.argument("hex", StringArgumentType.word())
                        .executes(ctx -> {
                            String hex = parseHex(argString(ctx, "hex"), ctx.getSource());
                            if (hex == null) return 0;
                            switch (piece) {
                                case "helmet" -> cfg.armor.helmetHex = hex;
                                case "chest"  -> cfg.armor.chestplateHex = hex;
                                case "legs"   -> cfg.armor.leggingsHex = hex;
                                case "boots"  -> cfg.armor.bootsHex = hex;
                                case "global" -> cfg.armor.globalHex = hex;
                            }
                            cfg.save();
                            ctx.getSource().sendFeedback(Component.literal("§aArmor " + piece + ": " + hex));
                            return 1;
                        }));
    }

    private LiteralArgumentBuilder<FabricClientCommandSource> buildNameCommand(SkyCustomizerConfig cfg) {
        return ClientCommands.literal("name")
                .then(ClientCommands.literal("customname")
                        .then(ClientCommands.argument("text", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    String text = argString(ctx, "text");
                                    cfg.name.customName = text;
                                    cfg.name.useCustomName = !text.isEmpty();
                                    cfg.save();
                                    ctx.getSource().sendFeedback(Component.literal("§aCustom name: " + text));
                                    return 1;
                                })))
                .then(ClientCommands.literal("color")
                        .then(ClientCommands.argument("hex", StringArgumentType.word())
                                .executes(ctx -> {
                                    String hex = parseHex(argString(ctx, "hex"), ctx.getSource());
                                    if (hex == null) return 0;
                                    cfg.name.colorHex = hex; cfg.save();
                                    ctx.getSource().sendFeedback(Component.literal("§aName color: " + hex));
                                    return 1;
                                })))
                .then(ClientCommands.literal("prefix")
                        .then(ClientCommands.argument("text", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    cfg.name.prefix = argString(ctx, "text"); cfg.save();
                                    ctx.getSource().sendFeedback(Component.literal("§aPrefix: " + cfg.name.prefix));
                                    return 1;
                                })))
                .then(ClientCommands.literal("suffix")
                        .then(ClientCommands.argument("text", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    cfg.name.suffix = argString(ctx, "text"); cfg.save();
                                    ctx.getSource().sendFeedback(Component.literal("§aSuffix: " + cfg.name.suffix));
                                    return 1;
                                })))
                .then(boolToggle("chroma",
                        b -> { cfg.name.chroma = b; if (b) cfg.name.rainbow = false; cfg.save(); },
                        "§aName chroma ON", "§cName chroma OFF"))
                .then(boolToggle("rainbow",
                        b -> { cfg.name.rainbow = b; if (b) cfg.name.chroma = false; cfg.save(); },
                        "§aName rainbow ON", "§cName rainbow OFF"));
    }

    private LiteralArgumentBuilder<FabricClientCommandSource> buildChatCommand(SkyCustomizerConfig cfg) {
        return ClientCommands.literal("chat")
                .then(boolToggle("level",
                        b -> { cfg.chat.levelColorInChat = b; cfg.save(); },
                        "§aChat level colors ON", "§cChat level colors OFF"))
                .then(boolToggle("recolor",
                        b -> { cfg.chat.recolorChatTypes = b; cfg.save(); },
                        "§aChat type recolor ON", "§cChat type recolor OFF"))
                .then(ClientCommands.literal("type")
                        .then(ClientCommands.argument("type", StringArgumentType.word())
                                .then(ClientCommands.argument("hex", StringArgumentType.word())
                                        .executes(ctx -> {
                                            String type = argString(ctx, "type").toUpperCase();
                                            String hex = parseHex(argString(ctx, "hex"), ctx.getSource());
                                            if (hex == null) return 0;
                                            cfg.chat.chatTypeColors.put(type, hex); cfg.save();
                                            ctx.getSource().sendFeedback(
                                                    Component.literal("§a" + type + " color: " + hex));
                                            return 1;
                                        }))))
                .then(ClientCommands.literal("highlight")
                        .then(ClientCommands.argument("word", StringArgumentType.word())
                                .then(ClientCommands.argument("hex", StringArgumentType.word())
                                        .executes(ctx -> {
                                            String word = argString(ctx, "word");
                                            String hex = parseHex(argString(ctx, "hex"), ctx.getSource());
                                            if (hex == null) return 0;
                                            cfg.chat.highlightWords.put(word, hex); cfg.save();
                                            ctx.getSource().sendFeedback(
                                                    Component.literal("§aHighlight '" + word + "': " + hex));
                                            return 1;
                                        }))))
                .then(boolToggle("timestamp",
                        b -> { cfg.chat.timestampEnabled = b; cfg.save(); },
                        "§aChat timestamps ON", "§cChat timestamps OFF"))
                .then(boolToggle("compact",
                        b -> { cfg.chat.compactChat = b; cfg.save(); },
                        "§aChat compaction ON", "§cChat compaction OFF"));
    }

    private LiteralArgumentBuilder<FabricClientCommandSource> buildScoreboardCommand(SkyCustomizerConfig cfg) {
        return ClientCommands.literal("scoreboard")
                .then(boolToggle("enabled",
                        b -> { cfg.scoreboard.enabled = b; cfg.save(); },
                        "§aScoreboard ON", "§cScoreboard OFF"))
                .then(ClientCommands.literal("title")
                        .then(ClientCommands.argument("text", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    cfg.scoreboard.customTitle = argString(ctx, "text"); cfg.save();
                                    ctx.getSource().sendFeedback(
                                            Component.literal("§aScoreboard title: " + cfg.scoreboard.customTitle));
                                    return 1;
                                })))
                .then(ClientCommands.literal("titlecolor")
                        .then(ClientCommands.argument("hex", StringArgumentType.word())
                                .executes(ctx -> {
                                    String hex = parseHex(argString(ctx, "hex"), ctx.getSource());
                                    if (hex == null) return 0;
                                    cfg.scoreboard.titleColorHex = hex; cfg.save();
                                    ctx.getSource().sendFeedback(Component.literal("§aTitle color: " + hex));
                                    return 1;
                                })))
                .then(boolToggle("chroma",
                        b -> { cfg.scoreboard.chromaTitle = b; cfg.save(); },
                        "§aChroma title ON", "§cChroma title OFF"))
                .then(boolToggle("hidenumbers",
                        b -> { cfg.scoreboard.hideScoreNumbers = b; cfg.save(); },
                        "§aScore numbers hidden", "§cScore numbers shown"));
    }

    private LiteralArgumentBuilder<FabricClientCommandSource> buildItemsCommand(SkyCustomizerConfig cfg) {
        return ClientCommands.literal("items")
                .then(buildHeldItemCommand("rename", false))
                .then(buildHeldItemCommand("lore", true))
                .then(ClientCommands.literal("reset").executes(ctx -> resetHeldItem(ctx.getSource())))
                .then(boolToggle("enabled",
                        b -> { cfg.items.enabled = b; cfg.save(); },
                        "§aItems ON", "§cItems OFF"))
                .then(boolToggle("hideglint",
                        b -> { cfg.items.hideGlint = b; cfg.save(); },
                        "§aEnchantment shine hidden", "§aEnchantment shine restored"))
                .then(boolToggle("forceglint",
                        b -> { cfg.items.forceGlint = b; cfg.save(); },
                        "§aForced enchantment shine ON", "§aForced enchantment shine OFF"))
                .then(ClientCommands.literal("rarity")
                        .then(ClientCommands.argument("rarity", StringArgumentType.word())
                                .then(ClientCommands.argument("hex", StringArgumentType.word())
                                        .executes(ctx -> {
                                            String rarity = argString(ctx, "rarity").toUpperCase();
                                            String hex = parseHex(argString(ctx, "hex"), ctx.getSource());
                                            if (hex == null) return 0;
                                            cfg.items.rarityColors.put(rarity, hex); cfg.save();
                                            ctx.getSource().sendFeedback(
                                                    Component.literal("§a" + rarity + " -> " + hex));
                                            return 1;
                                        }))));
    }

    private LiteralArgumentBuilder<FabricClientCommandSource> buildHeldItemCommand(String command, boolean lore) {
        return ClientCommands.literal(command)
                .then(ClientCommands.argument("text", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            var player = Minecraft.getInstance().player;
                            var source = ctx.getSource();
                            if (player == null || player.getMainHandItem().isEmpty()) {
                                source.sendError(Component.literal("Hold an item in your main hand first."));
                                return 0;
                            }
                            String text = argString(ctx, "text");
                            if (text.isBlank() || text.length() > (lore ? 1024 : 128)) {
                                source.sendError(Component.literal(lore ? "Lore must be 1 to 1024 characters." : "Name must be 1 to 128 characters."));
                                return 0;
                            }
                            var identity = ItemIdentity.of(player.getMainHandItem());
                            var live = SkyCustomizerConfig.get();
                            (lore ? live.items.heldItemLore : live.items.heldItemNames).put(identity.key(), text);
                            live.items.heldItemLabels.put(identity.key(), identity.originalName());
                            live.save();
                            source.sendFeedback(Component.literal(lore ? "Item lore saved" : "Item name saved")
                                    .withColor(0x55FF55).append(Component.literal(identity.unique()
                                            ? " for this item." : " for matching items of this type (no unique item ID).")));
                            if (!live.enabled || !live.items.enabled || !(lore ? live.items.loreEnabled : live.items.namesEnabled)) {
                                source.sendFeedback(Component.literal("Enable item customization in the config to see this override.").withColor(0xFFAA00));
                            }
                            return 1;
                        }));
    }

    private int resetHeldItem(FabricClientCommandSource source) {
        var player = Minecraft.getInstance().player;
        if (player == null || player.getMainHandItem().isEmpty()) {
            source.sendError(Component.literal("Hold an item in your main hand first."));
            return 0;
        }
        String key = ItemIdentity.of(player.getMainHandItem()).key();
        var cfg = SkyCustomizerConfig.get();
        cfg.items.heldItemNames.remove(key);
        cfg.items.heldItemLore.remove(key);
        cfg.items.heldItemLabels.remove(key);
        cfg.save();
        source.sendFeedback(Component.literal("Name and lore overrides cleared for the held item.").withColor(0x55FF55));
        return 1;
    }

    private LiteralArgumentBuilder<FabricClientCommandSource> buildVisualsCommand(SkyCustomizerConfig cfg) {
        return ClientCommands.literal("visuals")
                .then(ClientCommands.literal("hud")
                        .then(ClientCommands.literal("custom")
                                .then(ClientCommands.literal("true").executes(ctx -> {
                                    cfg.visuals.customHud = true; cfg.save();
                                    ctx.getSource().sendFeedback(Component.literal("§aCustom HUD ON")); return 1;
                                }))
                                .then(ClientCommands.literal("false").executes(ctx -> {
                                    cfg.visuals.customHud = false; cfg.save();
                                    ctx.getSource().sendFeedback(Component.literal("§cCustom HUD OFF")); return 1;
                                }))))
                .then(ClientCommands.literal("hidetitles")
                        .then(ClientCommands.literal("true").executes(ctx -> {
                            cfg.visuals.hideTitles = true; cfg.save();
                            ctx.getSource().sendFeedback(Component.literal("§aTitles hidden")); return 1;
                        }))
                        .then(ClientCommands.literal("false").executes(ctx -> {
                            cfg.visuals.hideTitles = false; cfg.save();
                            ctx.getSource().sendFeedback(Component.literal("§cTitles shown")); return 1;
                        })))
                .then(ClientCommands.literal("hidebossbar")
                        .then(ClientCommands.literal("true").executes(ctx -> {
                            cfg.visuals.bossbarHide = true; cfg.save();
                            ctx.getSource().sendFeedback(Component.literal("§aBossbar hidden")); return 1;
                        }))
                        .then(ClientCommands.literal("false").executes(ctx -> {
                            cfg.visuals.bossbarHide = false; cfg.save();
                            ctx.getSource().sendFeedback(Component.literal("§cBossbar shown")); return 1;
                        })))
                .then(ClientCommands.literal("hideactionbar")
                        .then(ClientCommands.literal("true").executes(ctx -> {
                            cfg.visuals.actionbarHide = true; cfg.save();
                            ctx.getSource().sendFeedback(Component.literal("§aActionbar hidden")); return 1;
                        }))
                        .then(ClientCommands.literal("false").executes(ctx -> {
                            cfg.visuals.actionbarHide = false; cfg.save();
                            ctx.getSource().sendFeedback(Component.literal("§cActionbar shown")); return 1;
                        })))
                .then(ClientCommands.literal("vignette")
                        .then(ClientCommands.literal("true").executes(ctx -> {
                            cfg.visuals.vignetteHide = true; cfg.save();
                            ctx.getSource().sendFeedback(Component.literal("§aVignette hidden")); return 1;
                        }))
                        .then(ClientCommands.literal("false").executes(ctx -> {
                            cfg.visuals.vignetteHide = false; cfg.save();
                            ctx.getSource().sendFeedback(Component.literal("§cVignette shown")); return 1;
                        })));
    }
}
