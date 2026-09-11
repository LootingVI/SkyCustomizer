package de.flori.skycustomizer.config;

import com.google.gson.Gson;
import de.flori.skycustomizer.features.ChatCustomizer;
import de.flori.skycustomizer.util.ColorUtil;
import de.flori.skycustomizer.util.ItemIdentity;
import de.flori.skycustomizer.util.RegexCache;
import de.flori.skycustomizer.util.TextUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;
import java.lang.reflect.Field;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

import static de.flori.skycustomizer.render.ModernRenderHelper.*;

public final class FeatureEditorScreen extends StudioScreen {
    public enum Page {
        ITEMS("Item Editor"), NAME("Nametag Editor"), ARMOR("Armor Editor"),
        CHAT("Chat Editor"), VISUALS("HUD & Text"), SCOREBOARD("Scoreboard Lines");
        final String title;
        Page(String title) { this.title = title; }
    }
    private static final Gson GSON = new Gson();
    private final Screen parent;
    private final Page page;
    private final SkyCustomizerConfig config;
    private Object draft;
    private final List<Row> rows = new ArrayList<>();
    private final List<Group> groups = new ArrayList<>();
    private EditBox search;
    private String query = "", status = "";
    private int scroll, totalHeight;
    private boolean dirty, built;
    private int px, py, pw, ph;
    private int fullW, previewW;
    private String selectedItem;
    private ItemStack heldPreview = ItemStack.EMPTY;
    private TextRow itemNameRow, itemLoreRow;
    private Component preview;
    private List<Component> previewTooltip;
    private final SidebarPreview sidebarPreview=new SidebarPreview();
    private boolean pendingRules=true;

    public FeatureEditorScreen(Screen parent, Page page) {
        super(page.title);
        this.parent = parent;
        this.page = page;
        config = SkyCustomizerConfig.get();
        Object original = switch (page) {
            case ITEMS -> config.items; case NAME -> config.name; case ARMOR -> config.armor;
            case CHAT -> config.chat; case VISUALS -> config.visuals; case SCOREBOARD -> config.scoreboard;
        };
        draft = GSON.fromJson(GSON.toJson(original), original.getClass());
    }
    public FeatureEditorScreen(Screen parent, Page page, String filter) { this(parent,page); query=filter.toLowerCase(Locale.ROOT); }

    @Override protected void init() {
        clearWidgets();
        layout();
        if (!built) {
            buildRows();
            built = true;
        }
        search = new EditBox(font, 0, 0, 100, 18, Component.literal("Search settings"));
        search.setMaxLength(80);
        search.setBordered(false);
        search.setTextShadow(false);
        search.setValue(query);
        search.setHint(Component.literal("Search settings..."));
        search.setResponder(text -> { query = text.toLowerCase(Locale.ROOT); scroll = 0; });
        addRenderableWidget(search);
        for (Row row : rows) row.register();
        startHistory();
    }

    private void layout() {
        fullW = Math.min(940, Math.max(240, width - 16));
        previewW = fullW >= 610 ? Math.min(270, fullW / 3) : 0;
        pw = fullW - previewW;
        ph = Math.min(540, Math.max(160, height - 16));
        px = (width - fullW) / 2;
        py = (height - ph) / 2;
    }
    private int top() { return py + (previewW > 0 ? 62 : 108); }
    private int bottom() { return py + ph - 43; }
    private int rowW() { return pw - 28; }
    private void changed() { dirty = true; preview = null; previewTooltip = null;pendingRules=true; status = ""; }
    private record FormState(String data,String selected,Map<String,String> texts,Map<String,List<String[]>> entries) {}
    @Override protected String captureDraft() {
        if(!built)return null;
        Map<String,String> texts=new LinkedHashMap<>();
        for(Row row:rows)if(row instanceof TextRow t)texts.put(t.title,t.box.getValue());
        Map<String,List<String[]>> values=new LinkedHashMap<>();
        for(Group group:groups){List<String[]> entries=new ArrayList<>();for(EntryRow row:group.entries)
            entries.add(new String[]{row.key.getValue(),row.value.getValue(),Boolean.toString(row.enabled)});
            values.put(group.field.getName(),entries);}
        return GSON.toJson(new FormState(GSON.toJson(draft),selectedItem,texts,values));
    }
    @Override protected void restoreDraft(String json) {
        FormState state=GSON.fromJson(json,FormState.class);
        draft=GSON.fromJson(state.data,draft.getClass());rebuildForm();
        if(page==Page.ITEMS)selectItem(state.selected);
        for(Group group:groups){
            List<String[]> values=state.entries.get(group.field.getName());if(values==null)continue;
            int index=rows.indexOf(group.entries.isEmpty()?rows.stream().filter(r->r instanceof AddRow a&&a.group==group).findFirst().orElseThrow():group.entries.getFirst());
            for(EntryRow entry:group.entries){rows.remove(entry);removeWidget(entry.key);removeWidget(entry.value);}group.entries.clear();
            for(String[] value:values){EntryRow row=new EntryRow(group,value[0],value[1]);row.enabled=Boolean.parseBoolean(value[2]);group.entries.add(row);rows.add(index++,row);row.register();}
        }
        for(Row row:rows)if(row instanceof TextRow t&&state.texts.containsKey(t.title))t.set(state.texts.get(t.title));
        changed();
    }
    private void rebuildForm(){String selected=selectedItem;rows.clear();groups.clear();built=false;init();if(page==Page.ITEMS)selectItem(selected);}

    private void buildRows() {
        switch (page) {
            case ITEMS -> {
                captureHeld();
                section("ITEM LIBRARY", "Select a saved item or use the item in your main hand.");
                rows.add(new LibraryRow());
                itemNameRow = new TextRow("Display name", "Color button inserts a color. &l bold, &o italic, &r reset.",
                        "", value -> { putItem(false, value); return null; }, false, true);
                itemLoreRow = new TextRow("Custom lore", "Use \\n for a new line. Added above the original tooltip.",
                        "", value -> { putItem(true, value); return null; }, false, true);
                itemNameRow.box.setMaxLength(128);
                itemLoreRow.box.setMaxLength(1024);
                rows.add(itemNameRow); rows.add(itemLoreRow);
                rows.add(new ActionRow("Visual Lore Editor", "Edit, format and reorder individual tooltip lines.", () -> {
                    if(selectedItem==null){status="Select an item first";return;}
                    minecraft.setScreen(new LoreEditorScreen(this, ((SkyCustomizerConfig.ItemConfig)draft).heldItemLore.getOrDefault(selectedItem,""),
                            itemNameRow.box.getValue(), value -> {itemLoreRow.set(value);putItem(true,value);changed();checkpoint();}));
                }));
                selectItem(selectedItem);
                section("ITEM APPEARANCE", "Local display only. Your actual item data stays intact.");
                fields("enabled,namesEnabled,loreEnabled,showOriginalName,hideLore,rarityColorsEnabled,hideGlint,forceGlint,itemIconsEnabled,itemIcon,favoriteMarkerEnabled,favoriteMarker");
                section("TOOLTIP STYLE", "Add a polished rarity accent to supported item tooltips.");
                fields("tooltipStyleEnabled,tooltipAccentHex");
                group("Rarity colors", "Color of item names, detected from the server rarity line.", "rarityColors", false, true, false);
                group("Favorite item types", "Minecraft item IDs that receive the favorite marker, for example minecraft:diamond_sword.", "favoriteItemTypes", false, false, true);
                group("Item type names", "Minecraft item ID → custom name. This runs before name pattern rules.", "itemTypeNames", false, false, false);
                group("Name rules", "Pattern → replacement. Use {original} to include the original name.", "customNames", true, false, false);
                group("Lore rules", "Original name contains → custom lore. Use \\n for line breaks.", "customLore", false, false, false);
                group("Advanced regex rules", "Rules run in order when no item override or name rule matched.", "regexRules", true, false, false);
            }
            case NAME -> {
                section("YOUR NAME", "Live preview above; changes are visible only to you.");
                fields("enabled,useCustomName,customName,colorHex,prefix,suffix,bold,italic,underline,strikethrough,obfuscated");
                section("COLOR EFFECTS", "Chroma is animated. Rainbow assigns a static color to each letter.");
                fields("chroma,chromaSpeed,rainbow");
                section("WHERE TO APPLY", "Choose which displays use your custom player name.");
                fields("applyInChat,applyInTab,applyOverHead,applyInScoreboard");
                section("PLAYER NAMETAGS", "Adjust existing player tags; hidden vanilla tags are not revealed.");
                fields("hidePlayerNametags,hideNametagScores,nametagMaxDistance,nametagOffsetY,recolorOtherPlayers,otherPlayerColorHex,nametagPill,nametagPillColorHex,nametagHeadIcon,nametagHeadSymbol,nametagSpacing,nametagDistanceFade,nametagFadeStart");
                group("Player aliases", "Exact Minecraft username → local overhead alias. Supports colors.", "playerAliases", false, false, false);
                group("Player badges", "Exact Minecraft username → icon shown before the nametag.", "playerBadges", false, false, false);
                group("Rank icons", "Rank text found in a tag → icon shown before the nametag.", "rankIcons", false, false, false);
                section("FRIENDS & GUILD", "Local display styles for players you choose.");
                fields("friendPrefix,friendColorHex,guildPrefix,guildColorHex");
                group("Friend usernames", "Exact usernames that receive the friend style.", "friendNames", false, false, true);
                group("Guild usernames", "Exact usernames that receive the guild style.", "guildNames", false, false, true);
                rows.add(new ActionRow("Nametag Rules", "Designs for yourself, exact players or level ranges.", () -> minecraft.setScreen(new NametagRulesScreen(this,
                        ((SkyCustomizerConfig.NameConfig)draft).rules, rules -> {((SkyCustomizerConfig.NameConfig)draft).rules=rules;changed();checkpoint();}))));
            }
            case ARMOR -> {
                section("DYED ARMOR", "Tint armor layers that carry a dye color.");
                fields("enabled,perPiece,globalHex,helmetHex,chestplateHex,leggingsHex,bootsHex,chroma,chromaSpeed");
            }
            case CHAT -> {
                section("MESSAGES", "Filters and formatting apply to new incoming messages.");
                fields("enabled,levelColorInChat,recolorChatTypes,compactChat,duplicateGrouping,timestampEnabled,timestampColorHex,timestampFormat,chatTheme,chatAccentHex,chatAccentBar,mentionSound,keywordSound");
                group("Chat colors", "Chat type → color, for example GUILD or PARTY.", "chatTypeColors", false, true, false);
                group("Highlights", "Word → highlight color.", "highlightWords", false, true, false);
                group("Hide containing", "Hide new messages containing any of these strings.", "hideMessageContains", false, false, true);
                group("Hide matching regex", "Hide new messages matching these patterns.", "hideMessageRegex", true, false, true);
                rows.add(new ActionRow("Copy Latest Message", "Copy the latest customized chat line to your clipboard.", () ->
                        status = ChatCustomizer.copyLatestMessage() ? "Latest chat message copied" : "No chat message to copy yet"));
            }
            case VISUALS -> {
                section("HUD & ACTIONBAR", "Use the HUD Editor to position elements by dragging.");
                fields("enabled,customHud,hudScale,hudOffsetX,hudOffsetY,actionbarCustom,actionbarHide");
                section("TITLES", "Colors and visibility for title and subtitle messages.");
                fields("customTitles,hideTitles,titleHex,subtitleHex,titleChroma");
                section("BOSSBAR", "Visibility of server boss bars.");
                fields("bossbarCustom,bossbarHide");
                section("SCREEN OVERLAY", "Hide Minecraft's dark screen-edge vignette.");
                fields("vignetteHide");
                section("PERFORMANCE WIDGET", "A compact HUD panel for FPS, ping and a local TPS estimate.");
                fields("performanceHud,performanceShowFps,performanceShowPing,performanceShowTps,performanceHudScale,performanceHudOffsetX,performanceHudOffsetY,performanceHudBackground,performanceHudBackgroundOpacity,performanceHudColorHex");
            }
            case SCOREBOARD -> {
                rows.add(new ActionRow("Visual Line Editor", "Select sidebar lines in a live preview to edit or hide them.", () -> {
                    for(Group group:groups) if(!group.flush()){status="Check rule keys first";return;}
                    minecraft.setScreen(new ScoreboardEditorScreen(this,(SkyCustomizerConfig.ScoreboardConfig)draft, value -> {draft=value;rebuildForm();changed();checkpoint();}));
                }));
                section("SIDEBAR CONTENT", "Rules preserve the server's text and colors on untouched lines.");
                section("LINE APPEARANCE", "Scale, recolor and animate every line without changing server data.");
                fields("lineFontScale,lineColorHex,scoreColorHex,lineAnimation,lineAnimationSpeed");
                group("Line icons", "Text found in a line → icon placed before that line.", "lineIcons", false, false, false);
                group("Hidden lines", "Hide sidebar lines containing any of these strings.", "hiddenLineContains", false, false, true);
                group("Hidden exact lines", "Hide rows matching this exact visible text.", "hiddenExactLines", false, false, true);
                group("Rename rules", "Pattern → replacement, with formatting codes.", "renameRules", true, false, false);
            }
        }
    }

    private void section(String title, String hint) { rows.add(new SectionRow(title, hint)); }
    private void fields(String fields) {
        for (String key : fields.split(",")) {
            try {
                Field field = draft.getClass().getField(key);
                String title = label(key);
                if (field.getType() == boolean.class) {
                    rows.add(new ToggleRow(title, hint(key), field.getBoolean(draft), value -> {
                        try { field.setBoolean(draft, value); } catch (IllegalAccessException e) { throw new IllegalStateException(e); }
                    }));
                } else {
                    boolean color = key.toLowerCase(Locale.ROOT).endsWith("hex");
                    String initial = String.valueOf(field.get(draft));
                    rows.add(new TextRow(title, hint(key), initial, text -> {
                        try {
                            Object value = text;
                            if (color) {
                                value = normalizeColor(text);
                                if (value == null) return "Use a six-digit hex color.";
                            } else if (field.getType() == int.class) {
                                value = Integer.parseInt(text.trim());
                            } else if (field.getType() == float.class) {
                                float number = Float.parseFloat(text.trim());
                                if (!Float.isFinite(number)) return "Enter a finite number.";
                                float min = key.equals("nametagOffsetY") ? -1 : (key.equals("nametagMaxDistance") || key.equals("nametagFadeStart")) ? 8 : .1f;
                                float max = key.equals("nametagOffsetY") ? 2 : (key.equals("nametagMaxDistance") || key.equals("nametagFadeStart")) ? 64 : 4;
                                if (number < min || number > max) return "Range: " + min + " to " + max;
                                value = number;
                            } else if (key.equals("timestampFormat")) {
                                java.time.format.DateTimeFormatter.ofPattern(text);
                            }
                            field.set(draft, value);
                            return null;
                        } catch (IllegalArgumentException e) { return "Invalid value."; }
                        catch (IllegalAccessException e) { throw new IllegalStateException(e); }
                    }, color, key.equals("customName") || key.equals("prefix") || key.equals("suffix")));
                }
            } catch (ReflectiveOperationException e) { throw new IllegalStateException("Unknown setting " + key, e); }
        }
    }

    private String hint(String key) {
        return switch (key) {
            case "namesEnabled" -> "Use saved item names and name rules.";
            case "loreEnabled" -> "Show your custom tooltip lines.";
            case "hideLore" -> "Hide original tooltip details, retaining custom lore.";
            case "showOriginalName" -> "Add the original name below a renamed item.";
            case "hideGlint" -> "Hide the enchantment shine; takes priority over Force Glint.";
            case "forceGlint" -> "Show enchantment shine on all items.";
            case "rarityColorsEnabled" -> "Recolor names using rarity detected from the original lore.";
            case "itemIconsEnabled" -> "Add the selected symbol before customized item names.";
            case "favoriteMarkerEnabled" -> "Show a marker for item types in your favorites list.";
            case "tooltipStyleEnabled" -> "Add rarity-colored header and footer accents to tooltip text.";
            case "nametagMaxDistance" -> "Maximum player-tag distance in blocks: 8–64.";
            case "nametagOffsetY" -> "Vertical offset in blocks: -1 to 2.";
            case "nametagPill" -> "Frame player tags with a colored pill-shaped text treatment.";
            case "nametagHeadIcon" -> "Show a small symbol before player tags.";
            case "nametagDistanceFade" -> "Dim player tags smoothly after the fade-start distance.";
            case "nametagFadeStart" -> "Start fading tags at this distance: 8–64 blocks.";
            case "hideNametagScores" -> "Hide the score or health line beneath player names.";
            case "hidePlayerNametags" -> "Hide player names and their score lines.";
            case "customName" -> "Leave empty to keep your username.";
            case "timestampFormat" -> "Example: HH:mm:ss";
            case "chatTheme" -> "CLASSIC leaves messages unchanged; ACCENT adds a colored marker.";
            case "chatAccentBar" -> "Add a compact accent marker before every incoming message.";
            case "mentionSound" -> "Play a quiet sound when your Minecraft name is mentioned.";
            case "keywordSound" -> "Play a quiet sound for configured highlight keywords.";
            case "duplicateGrouping" -> "Treat repeated messages as one compact chat entry.";
            case "lineFontScale" -> "Scale scoreboard line text from 0.6 to 2.0.";
            case "lineAnimation" -> "Pulse the configured line color smoothly.";
            case "lineAnimationSpeed" -> "Animation speed from 0.1 to 4.0.";
            case "performanceHud" -> "Show FPS, ping and a local TPS estimate in the HUD.";
            case "performanceShowFps", "performanceShowPing", "performanceShowTps" -> "Choose the performance values shown in the HUD.";
            case "performanceHudScale" -> "Scale the performance widget from 0.5 to 3.0.";
            case "performanceHudBackground" -> "Draw a subtle background behind the performance widget.";
            case "applyInScoreboard" -> "Customize your name inside sidebar lines.";
            default -> key.toLowerCase(Locale.ROOT).endsWith("hex") ? "Choose a color or enter #RRGGBB."
                    : "Changes take effect when you save.";
        };
    }

    private void group(String title, String hint, String field, boolean regex, boolean colors, boolean list) {
        Group group = new Group(title, hint, field, regex, colors, list);
        groups.add(group);
        rows.add(new SectionRow(title.toUpperCase(Locale.ROOT), hint));
        rows.addAll(group.entries);
        if (group.rules) rows.add(new SectionRow("RULE ORDER", "Use the row switch to enable or disable each rule."));
        rows.add(new AddRow(group));
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        layout();
        backdrop(g, delta);
        fillRect(g, 0, 0, width, height, 0xAD080604);
        fillRoundRect(g, px, py, fullW, ph, 10, C_PANEL);
        drawRoundBorder(g, px, py, fullW, ph, 10, C_BORDER);
        drawString(g, page.title, px + 14, py + 12, C_TEXT);
        String state = dirty ? "UNSAVED" : "LOCAL PREVIEW";
        drawString(g, state, px + pw - getStringWidth(state) - 14, py + 12, C_GOLD);
        fillRoundRect(g, px + 12, py + 30, pw - 24, 23, 5, C_INPUT);
        search.setX(px + 20); search.setY(py + 37); search.setWidth(pw - 40);
        search.extractRenderState(g, mx, my, delta);
        drawPreview(g);
        if(previewW>0) drawSidePreview(g);
        int y = top() - scroll;
        int extent = 0;
        g.enableScissor(px + 10, top(), px + pw - 10, bottom());
        for (Row row : rows) {
            boolean matches = query.isBlank() || row.searchText().toLowerCase(Locale.ROOT).contains(query);
            row.y = y;
            row.visible = matches && y + row.height() > top() && y < bottom();
            row.position();
            if (!matches) continue;
            if (row.visible) row.draw(g, mx, my, delta);
            y += row.height() + 5;
            extent += row.height() + 5;
        }
        g.disableScissor();
        totalHeight = extent;
        scroll = Math.clamp(scroll, 0, Math.max(0, totalHeight - (bottom() - top())));
        if (totalHeight > bottom() - top()) {
            int viewport = bottom() - top();
            int h = Math.max(12, viewport * viewport / totalHeight);
            fillRect(g, px + pw - 7, top() + (viewport - h) * scroll / Math.max(1, totalHeight - viewport), 2, h, C_GOLD_DARK);
        }
        int fy = py + ph - 35;
        button(g,px+14,fy,43,23,"Undo",false,mx,my);
        button(g,px+61,fy,43,23,"Redo",false,mx,my);
        button(g, px + pw - 76, fy, 62, 23, "Save", true, mx, my);
        button(g, px + pw - 144, fy, 62, 23, "Discard", false, mx, my);
        drawString(g, fit(status, pw - 270), px + 111, fy + 8,
                status.isEmpty() ? C_TEXT_DIM : C_GOLD);
        if (page == Page.ITEMS && previewW==0 && selectedItem != null && hit(mx, my, px + 12, py + 60, pw - 24, 39)) {
            if (previewTooltip == null) {
                var s = (SkyCustomizerConfig.ItemConfig) draft;
                previewTooltip = new ArrayList<>();
                previewTooltip.add(preview);
                String lore = s.heldItemLore.getOrDefault(selectedItem, "");
                if (!lore.isEmpty()) for (String line : lore.replace("\\n", "\n").split("\n", -1))
                    previewTooltip.add(TextUtil.parseLegacyFormatting(line, Style.EMPTY.withColor(0xAAAAAA)));
                previewTooltip.add(Component.literal(selectedItem.startsWith("uuid:")
                        ? "Applies to this unique item" : "Applies to matching items of this type").withColor(0x887766));
            }
            g.setComponentTooltipForNextFrame(font, previewTooltip, mx, my);
        }
    }

    private void drawPreview(GuiGraphicsExtractor g) {
        if(previewW==0) {fillRoundRect(g, px + 12, py + 60, pw - 24, 39, 6, C_INPUT);
        drawString(g, "PREVIEW", px + 21, py + 66, C_GOLD_DARK);}
        if (preview == null || page == Page.NAME && ((SkyCustomizerConfig.NameConfig) draft).chroma) {
            if (page == Page.ITEMS) {
                var s = (SkyCustomizerConfig.ItemConfig) draft;
                String raw = s.heldItemLabels.getOrDefault(selectedItem, selectedItem == null ? "Select an item below" : "Saved item");
                String name = s.heldItemNames.getOrDefault(selectedItem, raw);
                preview = TextUtil.parseLegacyFormatting(name.replace("{original}", raw), Style.EMPTY.withColor(0xFFFFFF));
            } else if (page == Page.NAME) {
                var n = (SkyCustomizerConfig.NameConfig) draft;
                String username = Minecraft.getInstance().getUser().getName();
                preview = namePreview(n, username);
            } else preview = Component.literal(page == Page.ARMOR ? "Pick colors for each armor piece."
                    : page == Page.SCOREBOARD ? "Edit the content rules for your sidebar."
                    : page == Page.CHAT ? "Filters affect newly received messages." : "HUD, title and bossbar appearance.").withColor(0xD1BDA8);
        }
        if(previewW==0){g.enableScissor(px + 20, py + 78, px + pw - 20, py + 95);
        g.text(minecraft.font, preview, px + 21, py + 81, -1, false);g.disableScissor();}
    }

    private void drawSidePreview(GuiGraphicsExtractor g) {
        int x=px+pw+8,w=previewW-22,y=py+39;
        fillRect(g,px+pw,py+30,1,ph-45,C_BORDER);
        drawString(g,"LIVE PREVIEW",x+8,y,C_GOLD);
        drawString(g,"SAVED",x+8,y+25,C_TEXT_DIM);
        Component saved=page==Page.NAME ? namePreview(config.name,minecraft.getUser().getName()) : page==Page.ITEMS
                ? TextUtil.parseLegacyFormatting(config.items.heldItemNames.getOrDefault(selectedItem,
                    ((SkyCustomizerConfig.ItemConfig)draft).heldItemLabels.getOrDefault(selectedItem,"Select an item")),Style.EMPTY) : Component.literal("Current settings");
        g.enableScissor(x,y+39,x+w,bottom());
        g.text(minecraft.font,saved,x+8,y+41,-1,false);
        drawString(g,"DRAFT",x+8,y+73,C_GOLD);
        g.text(minecraft.font,preview,x+8,y+91,-1,false);
        if(page==Page.ITEMS){
            String lore=((SkyCustomizerConfig.ItemConfig)draft).heldItemLore.getOrDefault(selectedItem,"");
            int lineY=y+110;
            for(String line:lore.replace("\\n","\n").split("\n",-1)){
                for(var wrapped:minecraft.font.split(TextUtil.parseLegacyFormatting(line,Style.EMPTY.withColor(0xAAAAAA)),w-16)){
                    if(lineY+10<bottom())g.text(minecraft.font,wrapped,x+8,lineY,-1,false);lineY+=12;
                }
            }
        } else if(page==Page.SCOREBOARD){
            var style=(SkyCustomizerConfig.ScoreboardConfig)draft;
            if(pendingRules){for(Group group:groups)group.flush();pendingRules=false;}
            var snap=sidebarPreview.get(style);
            var size=de.flori.skycustomizer.features.ScoreboardCustomizer.measure(snap,style);
            float scale=Math.min(1,Math.min((w-16f)/Math.max(1,size.width()),(bottom()-y-115f)/Math.max(1,size.height())));
            if(scale>0)de.flori.skycustomizer.features.ScoreboardCustomizer.draw(g,snap,style,x+8,y+113,scale);
        } else if(page==Page.ARMOR){
            var a=(SkyCustomizerConfig.ArmorConfig)draft;
            String[] colors={a.helmetHex,a.chestplateHex,a.leggingsHex,a.bootsHex};
            for(int i=0;i<4;i++){fillRoundRect(g,x+8+i*36,y+116,28,32,6,ColorUtil.fromHex(a.perPiece?colors[i]:a.globalHex));}
        }
        g.disableScissor();
        drawString(g,"Ctrl+Z / Ctrl+Y  Undo / Redo",x+8,py+ph-24,C_TEXT_DIM);
    }

    private Component namePreview(SkyCustomizerConfig.NameConfig n, String username) {
        String name = n.useCustomName && n.customName != null && !n.customName.isEmpty() ? n.customName : username;
        Style style = Style.EMPTY.withColor(ColorUtil.fromHex(n.colorHex) & 0xFFFFFF).withBold(n.bold).withItalic(n.italic)
                .withUnderlined(n.underline).withStrikethrough(n.strikethrough).withObfuscated(n.obfuscated);
        String text = Objects.toString(n.prefix, "") + name + Objects.toString(n.suffix, "");
        if (!n.chroma && !n.rainbow) return TextUtil.parseLegacyFormatting(text, style);
        String plain = TextUtil.stripLegacyFormatting(text);
        var out = Component.empty();
        for (int i = 0; i < plain.length(); i++) out.append(Component.literal(plain.substring(i, i + 1))
                .withStyle(style.withColor(n.chroma ? ColorUtil.chromaChar(i, n.chromaSpeed) & 0xFFFFFF
                        : java.awt.Color.HSBtoRGB((float) i / Math.max(1, plain.length()), 1, 1) & 0xFFFFFF)));
        return out;
    }

    private void captureHeld() {
        var player = Minecraft.getInstance().player;
        if (player == null || player.getMainHandItem().isEmpty()) {
            var s = (SkyCustomizerConfig.ItemConfig) draft;
            selectedItem = s.heldItemLabels.keySet().stream().findFirst().orElse(null);
            return;
        }
        heldPreview = player.getMainHandItem().copy();
        var id = ItemIdentity.of(heldPreview);
        var s = (SkyCustomizerConfig.ItemConfig) draft;
        s.heldItemLabels.put(id.key(), id.originalName());
        selectedItem = id.key();
    }
    private void selectItem(String key) {
        selectedItem = key;
        if (itemNameRow == null) return;
        var s = (SkyCustomizerConfig.ItemConfig) draft;
        itemNameRow.set(s.heldItemNames.getOrDefault(key, ""));
        itemLoreRow.set(s.heldItemLore.getOrDefault(key, ""));
        itemNameRow.box.setEditable(key != null); itemLoreRow.box.setEditable(key != null);
        preview = null;
        previewTooltip = null;
    }
    private void putItem(boolean lore, String value) {
        if (selectedItem == null) return;
        var s = (SkyCustomizerConfig.ItemConfig) draft;
        var map = lore ? s.heldItemLore : s.heldItemNames;
        if (value.isEmpty()) map.remove(selectedItem); else map.put(selectedItem, value);
    }

    private void save() {
        for (Row row : rows) {
            if (!row.validate()) { status = "Check highlighted fields"; query = ""; search.setValue(""); scroll = Math.max(0, row.y - top() + scroll - 8); return; }
        }
        for (Group group : groups) if (!group.flush()) { status = "Check duplicate or empty rule keys"; return; }
        Object saved = GSON.fromJson(GSON.toJson(draft), draft.getClass());
        switch (page) {
            case ITEMS -> config.items = (SkyCustomizerConfig.ItemConfig) saved;
            case NAME -> config.name = (SkyCustomizerConfig.NameConfig) saved;
            case ARMOR -> config.armor = (SkyCustomizerConfig.ArmorConfig) saved;
            case CHAT -> config.chat = (SkyCustomizerConfig.ChatConfig) saved;
            case VISUALS -> config.visuals = (SkyCustomizerConfig.VisualsConfig) saved;
            case SCOREBOARD -> config.scoreboard = (SkyCustomizerConfig.ScoreboardConfig) saved;
        }
        config.save(); dirty = false; status = "Changes saved";
    }

    @Override public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        if (e == null) return false;
        if (e.button() == 0) {
            int fy = py + ph - 35;
            if(hit(e.x(),e.y(),px+14,fy,43,23)){undo(false);return true;}
            if(hit(e.x(),e.y(),px+61,fy,43,23)){undo(true);return true;}
            if (hit(e.x(), e.y(), px + pw - 76, fy, 62, 23)) { save(); return true; }
            if (hit(e.x(), e.y(), px + pw - 144, fy, 62, 23)) { onClose(); return true; }
            if (e.y() >= top() && e.y() < bottom()) {
                for (Row row : List.copyOf(rows)) if (row.visible) {
                    if((row instanceof TextRow || row instanceof ToggleRow) && hit(e.x(),e.y(),px+pw-85,row.y+3,16,16)){
                        String id=page.name()+"|"+row.title;
                        if(!config.ui.favorites.remove(id))config.ui.favorites.add(id);config.save();return true;
                    }
                    if(row.click(e.x(),e.y()))return true;
                }
            }
        }
        if ((e.y() < top() || e.y() >= bottom()) && !hit(e.x(), e.y(), px + 12, py + 30, pw - 24, 23)) return true;
        return super.mouseClicked(e, doubleClick);
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (y >= top() && y < bottom()) {
            scroll = Math.clamp(scroll - (int) (vertical * 28), 0, Math.max(0, totalHeight - (bottom() - top())));
            setFocused(null);
            return true;
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }
    @Override public boolean keyPressed(KeyEvent event) {
        if (event.key() == 256) { onClose(); return true; }
        if (event.key() == 83 && (event.modifiers() & 2) != 0) { save(); return true; }
        return super.keyPressed(event);
    }
    @Override public void onClose() { Minecraft.getInstance().setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void extractBackground(GuiGraphicsExtractor g, int x, int y, float delta) {

    }

    private abstract class Row {
        final String title, hint;
        int y; boolean visible;
        Row(String title, String hint) { this.title = title; this.hint = hint; }
        int height() { return 49; }
        String searchText() { return title + " " + hint; }
        void register() {}
        void position() {}
        boolean validate() { return true; }
        boolean click(double x, double y) { return false; }
        abstract void draw(GuiGraphicsExtractor g, int mx, int my, float delta);
        void card(GuiGraphicsExtractor g) {
            fillRoundRect(g, px + 14, y, rowW(), height(), 6, C_SURFACE);
            drawRoundBorder(g, px + 14, y, rowW(), height(), 6, C_BORDER);
            if(this instanceof TextRow || this instanceof ToggleRow)
                icon(g,2,px+pw-85,y+3,15,config.ui.favorites.contains(page.name()+"|"+title)?C_GOLD:C_GOLD_DARK);
        }
    }
    private final class ActionRow extends Row {
        final Runnable action;
        ActionRow(String title,String hint,Runnable action){super(title,hint);this.action=action;}
        @Override void draw(GuiGraphicsExtractor g,int mx,int my,float dt){card(g);drawString(g,title,px+23,y+9,C_GOLD);drawString(g,fit(hint,rowW()-20),px+23,y+26,C_TEXT_DIM);}
        @Override boolean click(double x,double my){if(!hit(x,my,px+14,y,rowW(),height()))return false;checkpoint();action.run();return true;}
    }
    private final class SectionRow extends Row {
        SectionRow(String title, String hint) { super(title, hint); }
        @Override int height() { return 28; }
        @Override void draw(GuiGraphicsExtractor g, int mx, int my, float delta) {
            drawString(g, fit(title, rowW()), px + 17, y + 3, C_GOLD);
            drawString(g, fit(hint, rowW() - 6), px + 17, y + 15, C_TEXT_DIM);
        }
    }
    private final class ToggleRow extends Row {
        boolean value; final Consumer<Boolean> setter;
        ToggleRow(String title, String hint, boolean value, Consumer<Boolean> setter) {
            super(title, hint); this.value = value; this.setter = setter;
        }
        @Override int height() { return 37; }
        @Override void draw(GuiGraphicsExtractor g, int mx, int my, float delta) {
            card(g);
                drawString(g, fit(title, rowW() - 98), px + 23, y + 7, C_TEXT);
            drawString(g, fit(hint, rowW() - 62), px + 23, y + 20, C_TEXT_DIM);
            fillRoundRect(g, px + pw - 54, y + 10, 30, 16, 7, value ? C_GOLD_DARK : C_INPUT);
            fillRoundRect(g, px + pw - (value ? 37 : 51), y + 12, 12, 12, 6, value ? C_TEXT : C_TEXT_DIM);
        }
        @Override boolean click(double x, double my) {
            if (!hit(x, my, px + 14, y, rowW(), height())) return false;
            value = !value; setter.accept(value); changed(); return true;
        }
    }
    private final class TextRow extends Row {
        final EditBox box;
        final Function<String, String> setter;
        final boolean color, formatting;
        String error; boolean loading;
        TextRow(String title, String hint, String value, Function<String, String> setter, boolean color, boolean formatting) {
            super(title, hint); this.setter = setter; this.color = color; this.formatting = formatting;
            box = edit(title, value);
            box.setResponder(text -> { if (!loading) { error = setter.apply(text); changed(); } });
        }
        void set(String value) { loading = true; box.setValue(value); box.moveCursorToStart(false);error = null; loading = false; }
        @Override int height() { return 67; }
        @Override void register() { addRenderableWidget(box); }
        @Override void position() {
            box.visible = visible;
            if (!visible) return;
            box.setX(px + 29); box.setY(y + 44);
            box.setWidth(rowW() - (color || formatting ? 62 : 28));
        }
        @Override void draw(GuiGraphicsExtractor g, int mx, int my, float delta) {
            card(g);
            drawString(g, fit(title, rowW() - 88), px + 23, y + 7, C_TEXT);
            drawString(g, fit(error == null ? hint : error, rowW() - 16), px + 23, y + 20, error == null ? C_TEXT_DIM : C_RED);
            fillRoundRect(g, px + 23, y + 35, rowW() - (color || formatting ? 48 : 18), 25, 4, C_INPUT);
            if (error != null) drawBorder(g, px + 23, y + 35, box.getWidth() + 12, 25, C_RED);
            box.extractRenderState(g, mx, my, delta);
            if (color || formatting) {
                int swatch = color && normalizeColor(box.getValue()) != null ? ColorUtil.fromHex(box.getValue()) : C_GOLD;
                fillRoundRect(g, px + pw - 49, y + 36, 25, 23, 5, 0xFF000000 | swatch);
                if (formatting) drawCenteredString(g, "+", px + pw - 36, y + 44, C_TEXT);
            }
        }
        @Override boolean validate() { error = setter.apply(box.getValue()); return error == null; }
        @Override boolean click(double x, double my) {
            if (!(color || formatting) || !hit(x, my, px + pw - 49, y + 36, 25, 23)) return false;
            openPicker(box, color); return true;
        }
    }
    private final class LibraryRow extends Row {
        LibraryRow() { super("Selected item", "Saved items • Use held • Reset"); }
        @Override int height() { return 56; }
        @Override void draw(GuiGraphicsExtractor g, int mx, int my, float delta) {
            card(g);
            var s = (SkyCustomizerConfig.ItemConfig) draft;
            drawString(g, fit(s.heldItemLabels.getOrDefault(selectedItem, "Hold an item, then click Use held"), rowW() - 20), px + 23, y + 8, C_TEXT);
            button(g, px + 23, y + 27, 27, 20, "<", false, mx, my);
            button(g, px + 54, y + 27, 27, 20, ">", false, mx, my);
            button(g, px + 89, y + 27, 82, 20, "Use held", true, mx, my);
            button(g, px + 177, y + 27, 55, 20, "Reset", false, mx, my);
        }
        @Override boolean click(double x, double my) {
            var s = (SkyCustomizerConfig.ItemConfig) draft;
            if (hit(x, my, px + 89, y + 27, 82, 20)) {
                var player = Minecraft.getInstance().player;
                if (player == null || player.getMainHandItem().isEmpty()) { status = "Hold an item in your main hand first"; return true; }
                captureHeld(); selectItem(selectedItem); changed(); return true;
            }
            if (hit(x, my, px + 177, y + 27, 55, 20)) {
                s.heldItemNames.remove(selectedItem); s.heldItemLore.remove(selectedItem); selectItem(selectedItem); changed(); return true;
            }
            if (hit(x, my, px + 23, y + 27, 58, 20)) {
                List<String> keys = new ArrayList<>(s.heldItemLabels.keySet());
                for (String key : s.heldItemNames.keySet()) if (!keys.contains(key)) keys.add(key);
                for (String key : s.heldItemLore.keySet()) if (!keys.contains(key)) keys.add(key);
                if (!keys.isEmpty()) selectItem(keys.get(Math.floorMod(keys.indexOf(selectedItem) + (x < px + 52 ? -1 : 1), keys.size())));
                return true;
            }
            return false;
        }
    }

    private final class Group {
        final String title, hint; final Field field;
        final boolean regex, colors, list, rules;
        final List<EntryRow> entries = new ArrayList<>();
        @SuppressWarnings("unchecked")
        Group(String title, String hint, String key, boolean regex, boolean colors, boolean list) {
            this.title = title; this.hint = hint; this.regex = regex; this.colors = colors; this.list = list;
            rules = key.equals("regexRules");
            try {
                field = draft.getClass().getField(key);
                if (rules) for (var rule : (List<SkyCustomizerConfig.ItemConfig.RegexRule>) field.get(draft)) {
                    var row = new EntryRow(this, rule.pattern, rule.replacement); row.enabled = rule.enabled; entries.add(row);
                }
                else if (list) for (String value : (List<String>) field.get(draft)) entries.add(new EntryRow(this, "", value));
                else for (var entry : ((Map<String, String>) field.get(draft)).entrySet()) entries.add(new EntryRow(this, entry.getKey(), entry.getValue()));
            } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
        }
        boolean flush() {
            Map<String, String> values = new LinkedHashMap<>();
            List<String> lines = new ArrayList<>();
            List<SkyCustomizerConfig.ItemConfig.RegexRule> patterns = new ArrayList<>();
            for (EntryRow row : entries) {
                String key = row.key.getValue(), value = row.value.getValue();
                if (key.isEmpty() && value.isEmpty()) continue;
                if (!list && key.isEmpty() || !list && !rules && values.containsKey(key)) { row.error = true; return false; }
                if (rules) {
                    var rule = new SkyCustomizerConfig.ItemConfig.RegexRule(key, value); rule.enabled = row.enabled; patterns.add(rule);
                } else if (list) lines.add(value); else values.put(key, colors ? normalizeColor(value) : value);
            }
            try { field.set(draft, rules ? patterns : list ? lines : values); }
            catch (IllegalAccessException e) { throw new IllegalStateException(e); }
            return true;
        }
    }
    private final class EntryRow extends Row {
        final Group group; final EditBox key, value;
        boolean error, enabled = true;
        EntryRow(Group group, String key, String value) {
            super(group.title, group.hint); this.group = group;
            this.key = edit("Pattern or key", key); this.value = edit("Value", value);
            this.key.setResponder(text -> { error = false; changed(); });
            this.value.setResponder(text -> { error = false; changed(); });
        }
        @Override String searchText() { return super.searchText() + " " + key.getValue() + " " + value.getValue(); }
        @Override void register() { if (!group.list) addRenderableWidget(key); addRenderableWidget(value); }
        @Override void position() {
            key.visible = visible && !group.list; value.visible = visible;
            if (!visible) return;
            int usable = rowW() - 68;
            key.setX(px + 29); key.setY(y + 25); key.setWidth(usable * 2 / 5 - 10);
            value.setX(group.list ? px + 29 : px + 29 + usable * 2 / 5);
            value.setY(y + 25); value.setWidth(group.list ? usable - 10 : usable * 3 / 5 - 10);
        }
        @Override void draw(GuiGraphicsExtractor g, int mx, int my, float delta) {
            card(g);
            drawString(g, group.list ? (group.regex ? "PATTERN" : "TEXT") : "MATCH                         REPLACEMENT",
                    px + 25, y + 6, error ? C_RED : C_TEXT_DIM);
            if (error) drawBorder(g, px + 14, y, rowW(), height(), C_RED);
            if (!group.list) {
                fillRoundRect(g, key.getX() - 4, y + 17, key.getWidth() + 8, 23, 4, C_INPUT);
                key.extractRenderState(g, mx, my, delta);
            }
            fillRoundRect(g, value.getX() - 4, y + 17, value.getWidth() + 8, 23, 4, C_INPUT);
            value.extractRenderState(g, mx, my, delta);
            button(g, px + pw - 42, y + 18, 20, 22, "X", false, mx, my);
            button(g, px + pw - 67, y + 18, 21, 22, group.rules ? (enabled ? "ON" : "OFF") : "+", false, mx, my);
        }
        @Override boolean validate() {
            if (key.getValue().isEmpty() && value.getValue().isEmpty()) return true;
            error = group.regex && (!group.rules || enabled) && RegexCache.compile(group.list ? value.getValue() : key.getValue()) == null
                    || group.colors && normalizeColor(value.getValue()) == null;
            return !error;
        }
        @Override boolean click(double x, double my) {
            if (hit(x, my, px + pw - 42, y + 18, 20, 22)) {
                group.entries.remove(this); rows.remove(this); removeWidget(key); removeWidget(value); changed(); return true;
            }
            if (hit(x, my, px + pw - 67, y + 18, 21, 22)) {
                if (group.rules) { enabled = !enabled; changed(); }
                else openPicker(value, group.colors);
                return true;
            }
            return false;
        }
    }
    private final class AddRow extends Row {
        final Group group;
        AddRow(Group group) { super("Add " + group.title, group.hint); this.group = group; }
        @Override int height() { return 26; }
        @Override void draw(GuiGraphicsExtractor g, int mx, int my, float delta) {
            button(g, px + 15, y, Math.min(180, rowW()), 24, "+ Add entry", false, mx, my);
        }
        @Override boolean click(double x, double my) {
            if (!hit(x, my, px + 15, y, Math.min(180, rowW()), 24)) return false;
            EntryRow entry = new EntryRow(group, "", "");
            group.entries.add(entry); rows.add(rows.indexOf(this), entry); entry.register(); changed(); return true;
        }
    }

    private EditBox edit(String title, String text) {
        EditBox box = new EditBox(font, 0, 0, 100, 16, Component.literal(title));
        box.setMaxLength(2048); box.setBordered(false); box.setTextShadow(false);box.setTextColor(C_TEXT);
        box.setValue(text == null ? "" : text); box.moveCursorToStart(false);return box;
    }
    private void openPicker(EditBox box, boolean hexField) {
        Minecraft.getInstance().setScreen(new ColorPickerScreen(this, hexField ? box.getValue() : "#FFAA00", color -> {
            if (hexField) box.setValue(color);
            else box.insertText("&" + color);
        }));
    }
    private String fit(String text, int w) { return font.plainSubstrByWidth(text == null ? "" : text, Math.max(0, w)); }
    private static String label(String field) {
        String text = field.replaceAll("([a-z])([A-Z])", "$1 $2");
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
    static String normalizeColor(String text) {
        if(text==null)return null;
        String s = text.trim();
        if (s.startsWith("#")) s = s.substring(1);
        if (s.length() != 6) return null;
        for (int i = 0; i < 6; i++) if (Character.digit(s.charAt(i), 16) < 0) return null;
        return "#" + s.toUpperCase(Locale.ROOT);
    }
    private static boolean hit(double x, double y, int left, int top, int w, int h) {
        return x >= left && x < left + w && y >= top && y < top + h;
    }
    private void button(Object g, int x, int y, int w, int h, String text, boolean active, int mx, int my) {
        fillRoundRect(g, x, y, w, h, 5, active ? C_ACTIVE : hit(mx, my, x, y, w, h) ? C_HOVER : C_INPUT);
        drawRoundBorder(g, x, y, w, h, 5, active ? C_GOLD_DARK : C_BORDER);
        drawCenteredString(g, text, x + w / 2, y + (h - 8) / 2, active ? C_GOLD : C_TEXT);
    }
}
