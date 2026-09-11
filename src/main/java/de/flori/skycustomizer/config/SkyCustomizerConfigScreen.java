package de.flori.skycustomizer.config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static de.flori.skycustomizer.render.ModernRenderHelper.*;

public class SkyCustomizerConfigScreen extends StudioScreen {

    private static final int MAX_PANEL_W = 760;
    private static final int MAX_PANEL_H = 500;
    private static final int HEADER_H = 82;
    private static final int FOOTER_H = 42;
    private static final int PANEL_R = 8;
    private static final int ROW_H = 52;
    private static final int ROW_GAP = 6;

    private static final String[] TAB_NAMES = {
            "General", "Name", "Levels", "Scoreboard", "Items",
            "Armor", "Chat", "Visuals", "Presets"
    };
    private static final String[] INPUT_PLACEHOLDER = {
            "Keep your Minecraft name", "#55FFFF", "Example: [VIP] ", "Example:  [MVP+]",
            "1.0", "#555555", "SKYBLOCK", "#55FF55"
    };

    private final Screen parent;
    private final SkyCustomizerConfig cfg;
    private final String[] inputs = new String[8];
    private final boolean[] toggles = new boolean[13];
    private final EditBox[] inputFields = new EditBox[8];

    private int panelW;
    private int panelH;
    private int px;
    private int py;
    private int tab;
    private double scroll;
    private double targetScroll;
    private boolean dirty;
    private String statusMessage = "";
    private int statusTimer;
    private EditBox search;
    private String searchQuery = "";
    private boolean syncingFields;

    private enum SettingKind { TOGGLE, INPUT, INFO, ACTION }

    private record Setting(String section, String title, String description,
                           SettingKind kind, int index, String chip) { }

    public SkyCustomizerConfigScreen(Screen parent) {
        super("SkyCustomizer");
        this.parent = parent;
        this.cfg = SkyCustomizerConfig.get();
    }

    @Override
    protected void init() {
        clearWidgets();
        load();
        createTextFields();
        createSearchField();
        startHistory();
    }

    @Override protected String[] historyFields() { return new String[]{"inputs", "toggles"}; }
    @Override protected void afterHistory() { syncInputFields(); dirty = true; }

    private void load() {
        inputs[0] = cfg.name.customName;
        inputs[1] = cfg.name.colorHex;
        inputs[2] = cfg.name.prefix;
        inputs[3] = cfg.name.suffix;
        inputs[4] = String.valueOf(cfg.level.chromaSpeed);
        inputs[5] = cfg.level.bracketColorHex;
        inputs[6] = cfg.scoreboard.customTitle;
        inputs[7] = cfg.scoreboard.titleColorHex;

        toggles[0] = cfg.name.enabled;
        toggles[1] = cfg.name.useCustomName;
        toggles[2] = cfg.name.chroma;
        toggles[3] = cfg.name.rainbow;
        toggles[4] = cfg.level.enabled;
        toggles[5] = cfg.level.overrideHypixelColors;
        toggles[6] = cfg.level.chromaFor600Plus;
        toggles[7] = cfg.chat.levelColorInChat;
        toggles[8] = cfg.scoreboard.enabled;
        toggles[9] = cfg.armor.enabled;
        toggles[10] = cfg.armor.chroma;
        toggles[11] = cfg.visuals.customHud;
        toggles[12] = cfg.enabled;

        dirty = false;
    }

    private void createTextFields() {
        for (int i = 0; i < inputFields.length; i++) {
            final int index = i;
            EditBox field = new EditBox(font, 0, 0, 100, 16, Component.literal(INPUT_PLACEHOLDER[i]));
            field.setMaxLength(i == 4 ? 16 : 1024);
            field.setBordered(false);
            field.setTextShadow(false);
            field.setTextColor(C_TEXT);
            field.setHint(Component.literal(INPUT_PLACEHOLDER[i]));
            field.setResponder(text -> {
                inputs[index] = text;
                if (!syncingFields) {
                    dirty = true;
                }
            });
            inputFields[i] = field;
            addRenderableWidget(field);
        }
        syncInputFields();
    }

    private void createSearchField() {
        search = new EditBox(font, 0, 0, 100, 16, Component.literal("Search settings"));
        search.setMaxLength(80);
        search.setBordered(false);
        search.setTextShadow(false);
        search.setTextColor(C_TEXT);
        search.setHint(Component.literal("Search settings..."));
        search.setValue(searchQuery);
        search.setResponder(text -> {
            searchQuery = text.toLowerCase(Locale.ROOT);
            scroll = 0;
            targetScroll = 0;
        });
        addRenderableWidget(search);
    }

    private void syncInputFields() {
        if (inputFields[0] == null) {
            return;
        }
        syncingFields = true;
        for (int i = 0; i < inputFields.length; i++) {
            inputFields[i].setValue(inputs[i] == null ? "" : inputs[i]);
        }
        syncingFields = false;
    }

    private void save() {
        cfg.name.customName = value(0);
        cfg.name.colorHex = value(1);
        cfg.name.prefix = value(2);
        cfg.name.suffix = value(3);
        cfg.level.bracketColorHex = value(5);
        cfg.scoreboard.customTitle = value(6);
        cfg.scoreboard.titleColorHex = value(7);
        try {
            cfg.level.chromaSpeed = Float.parseFloat(value(4));
        } catch (NumberFormatException ignored) {

        }

        cfg.name.enabled = toggles[0];
        cfg.name.useCustomName = toggles[1];
        cfg.name.chroma = toggles[2];
        cfg.name.rainbow = toggles[3];
        cfg.level.enabled = toggles[4];
        cfg.level.overrideHypixelColors = toggles[5];
        cfg.level.chromaFor600Plus = toggles[6];
        cfg.chat.levelColorInChat = toggles[7];
        cfg.scoreboard.enabled = toggles[8];
        cfg.armor.enabled = toggles[9];
        cfg.armor.chroma = toggles[10];
        cfg.visuals.customHud = toggles[11];
        cfg.enabled = toggles[12];
        cfg.save();

        dirty = false;
        statusMessage = "Changes saved";
        statusTimer = 70;
    }

    private String value(int index) {
        return inputs[index] == null ? "" : inputs[index].trim();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        backdrop(graphics,delta);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        drawCustomOverlay(graphics, mouseX, mouseY, delta);
    }

    public void drawCustomOverlay(Object graphics, int mouseX, int mouseY, float delta) {
        panelW = Math.min(MAX_PANEL_W, width - 16);
        panelH = Math.min(MAX_PANEL_H, height - 16);
        px = (width - panelW) / 2;
        py = (height - panelH) / 2;

        if (statusTimer > 0) {
            statusTimer--;
        } else {
            statusMessage = "";
        }
        scroll = cfg.ui.animations ? scroll + (targetScroll - scroll) * 0.22 : targetScroll;

        fillRect(graphics, 0, 0, width, height, 0xA8000000);
        drawShadow(graphics, px, py, panelW, panelH, PANEL_R);
        fillRoundRect(graphics, px, py, panelW, panelH, PANEL_R, C_PANEL);
        drawRoundBorder(graphics, px, py, panelW, panelH, PANEL_R, C_BORDER);

        drawHeader(graphics, mouseX, mouseY);
        drawPage(graphics, mouseX, mouseY);
        drawFooter(graphics, mouseX, mouseY);
    }

    private void drawHeader(Object graphics, int mouseX, int mouseY) {
        fillRoundRect(graphics, px, py, panelW, HEADER_H, PANEL_R, 0xFF19130E);
        fillRect(graphics, px + PANEL_R, py + HEADER_H - 1, panelW - PANEL_R * 2, 1, C_BORDER);

        int logoX = px + 14;
        fillRoundRect(graphics, logoX, py + 11, 29, 29, 8, C_GOLD_DIM);
        drawRoundBorder(graphics, logoX, py + 11, 29, 29, 8, 0x665D3820);
        drawCenteredString(graphics, "SC", logoX + 14, py + 21, C_GOLD);

        int size = navSize();
        int x = navStart();
        for (int i = 0; i < TAB_NAMES.length; i++) {
            boolean selected = tab == i;
            boolean hovered = hv(mouseX, mouseY, x, py + 12, size, 28);
            int background = selected ? C_ACTIVE : (hovered ? C_HOVER : 0);
            if (background != 0) {
                fillRoundRect(graphics, x, py + 12, size, 28, 7, background);
            }
            if (selected) {
                fillRoundRect(graphics, x + 4, py + 37, size - 8, 2, 1, C_GOLD);
            }
            drawTabIcon(graphics, x + size / 2, py + 26, i,
                    selected ? C_GOLD : (hovered ? C_TEXT : C_TEXT_DIM));
            x += size + navGap();
        }

        int closeX = px + panelW - 34;
        boolean closeHovered = hv(mouseX, mouseY, closeX, py + 15, 20, 20);
        fillRoundRect(graphics, closeX, py + 15, 20, 20, 6,
                closeHovered ? 0xFF342016 : C_SURFACE);
        drawRoundBorder(graphics, closeX, py + 15, 20, 20, 6,
                closeHovered ? C_GOLD : C_BORDER);
        icon(graphics, 9, closeX + 3, py + 18, 14, closeHovered ? C_GOLD : C_TEXT_DIM);

        int searchX = searchX();
        int searchY = searchY();
        boolean searchFocused = getFocused() == search;
        fillRoundRect(graphics, searchX, searchY, searchW(), 24, 7, searchFocused ? C_INPUT_ACT : C_INPUT);
        drawRoundBorder(graphics, searchX, searchY, searchW(), 24, 7, searchFocused ? C_GOLD : C_BORDER);
        drawString(graphics, "SEARCH", searchX + 8, searchY + 8, searchFocused ? C_GOLD : C_TEXT_DIM);
        search.setX(searchX + 54);
        search.setY(searchY + 5);
        search.setWidth(searchW() - 62);
        search.visible = true;
        search.extractRenderState((GuiGraphicsExtractor) graphics, mouseX, mouseY, 0);
    }

    private void drawTabIcon(Object graphics, int centerX, int centerY, int icon, int color) {
        icon(graphics, icon, centerX - 9, centerY - 9, 18, color);
    }

    private void drawPage(Object graphics, int mouseX, int mouseY) {
        if (tab == 8 && searchQuery.isBlank()) {
            drawPresetsPage(graphics, mouseX, mouseY);
            return;
        }

        int y = pageTop();
        List<Setting> displayed = settingsForDisplay();
        if (displayed.isEmpty()) {
            drawString(graphics, "No settings match \"" + fitting(searchQuery, contentW() - 130) + "\".",
                    contentX() + 12, y + 16, C_TEXT_DIM);
        }
        for (Setting setting : displayed) {
            if (setting.section() != null) {
                drawSectionLabel(graphics, setting.section(), y);
                y += 17;
            }
            drawSettingRow(graphics, mouseX, mouseY, y, setting);
            y += ROW_H + ROW_GAP;
        }
        drawScrollbar(graphics, pageHeight());
    }

    private void drawSettingRow(Object graphics, int mouseX, int mouseY, int y, Setting setting) {
        if (!visible(y, ROW_H)) {
            return;
        }
        int x = contentX();
        int w = contentW();
        boolean hovered = hv(mouseX, mouseY, x, y, w, ROW_H);
        fillRoundRect(graphics, x, y, w, ROW_H, 7, hovered ? C_HOVER : C_SURFACE);
        drawRoundBorder(graphics, x, y, w, ROW_H, 7, hovered ? C_BORDER_ACT : C_BORDER);

        drawString(graphics, setting.title(), x + 12, y + 12, C_TEXT);
        drawString(graphics, fitting(setting.description(), controlX() - x - 28), x + 12, y + 27, C_TEXT_DIM);

        switch (setting.kind()) {
            case TOGGLE -> drawToggle(graphics, y, toggles[setting.index()]);
            case INPUT -> drawInputControl(graphics, y, setting.index());
            case INFO -> drawChip(graphics, y, setting.chip());
            case ACTION -> drawActionButton(graphics, y, setting.chip());
        }
    }

    private void drawToggle(Object graphics, int y, boolean on) {
        int trackW = 36;
        int trackH = 18;
        int x = contentRight() - trackW - 14;
        int trackY = y + (ROW_H - trackH) / 2;
        String label = on ? "ON" : "OFF";
        drawString(graphics, label, x - getStringWidth(label) - 10, trackY + 5, on ? C_GOLD : C_TEXT_DIM);
        fillRoundRect(graphics, x, trackY, trackW, trackH, 9, on ? C_GOLD_DARK : C_INPUT);
        drawRoundBorder(graphics, x, trackY, trackW, trackH, 9, on ? C_GOLD : C_BORDER);
        int knobX = on ? x + trackW - 16 : x + 2;
        fillRoundRect(graphics, knobX, trackY + 2, 14, 14, 7, on ? 0xFFFFE8D0 : 0xFF715A47);
    }

    private void drawInputControl(Object graphics, int y, int index) {
        int x = controlX();
        int w = controlW();
        EditBox field = inputFields[index];
        boolean focused = getFocused() == field;
        fillRoundRect(graphics, x, y + 14, w, 24, 7, focused ? C_INPUT_ACT : C_INPUT);
        drawRoundBorder(graphics, x, y + 14, w, 24, 7, focused ? C_GOLD : C_BORDER);
        field.setX(x + 7);
        field.setY(y + 19);
        field.setWidth(w - 14);
        field.visible = true;
        field.extractRenderState((GuiGraphicsExtractor) graphics, 0, 0, 0);
    }

    private void drawChip(Object graphics, int y, String label) {
        int w = Math.max(52, getStringWidth(label) + 18);
        int x = contentRight() - w - 12;
        fillRoundRect(graphics, x, y + 15, w, 22, 7, C_INPUT);
        drawRoundBorder(graphics, x, y + 15, w, 22, 7, C_BORDER);
        drawCenteredString(graphics, label, x + w / 2, y + 22, C_TEXT_DIM);
    }

    private void drawActionButton(Object graphics, int y, String label) {
        int w = Math.max(62, getStringWidth(label) + 20);
        int x = contentRight() - w - 12;
        fillRoundRect(graphics, x, y + 14, w, 24, 7, C_GOLD);
        drawRoundBorder(graphics, x, y + 14, w, 24, 7, 0xFFFFB56E);
        drawCenteredString(graphics, label, x + w / 2, y + 22, 0xFF24170E);
    }

    private void drawSectionLabel(Object graphics, String title, int y) {
        if (visible(y, 13)) {
            drawString(graphics, title.toUpperCase(), contentX() + 3, y + 2, C_GOLD_DARK);
        }
    }

    private void drawPresetsPage(Object graphics, int mouseX, int mouseY) {
        int y = pageTop();
        drawSectionLabel(graphics, "Preset library", y);
        y += 17;

        String active = cfg.presets.currentPreset == null || cfg.presets.currentPreset.isBlank()
                ? "No active preset" : cfg.presets.currentPreset;
        drawStaticRow(graphics, mouseX, mouseY, y, "Active Preset",
                "The current configuration can be saved or loaded as a preset.", active);
        y += ROW_H + ROW_GAP + 10;

        drawSectionLabel(graphics, "Saved presets", y);
        y += 17;
        if (cfg.presets.savedPresets.isEmpty()) {
            drawStaticRow(graphics, mouseX, mouseY, y, "No saved presets",
                    "Use /sc preset <name> save to create your first preset.", "COMMAND");
        } else {
            for (String preset : cfg.presets.savedPresets) {
                drawStaticRow(graphics, mouseX, mouseY, y, preset,
                        "Load this preset into the current configuration.", "LOAD");
                y += ROW_H + ROW_GAP;
            }
        }
        drawScrollbar(graphics, pageHeight());
    }

    private void drawStaticRow(Object graphics, int mouseX, int mouseY, int y,
                               String title, String description, String chip) {
        if (!visible(y, ROW_H)) {
            return;
        }
        int x = contentX();
        int w = contentW();
        boolean hovered = hv(mouseX, mouseY, x, y, w, ROW_H);
        fillRoundRect(graphics, x, y, w, ROW_H, 7, hovered ? C_HOVER : C_SURFACE);
        drawRoundBorder(graphics, x, y, w, ROW_H, 7, hovered ? C_BORDER_ACT : C_BORDER);
        drawString(graphics, fitting(title, controlX() - x - 28), x + 12, y + 12, C_TEXT);
        drawString(graphics, fitting(description, controlX() - x - 28), x + 12, y + 27, C_TEXT_DIM);
        drawChip(graphics, y, chip);
    }

    private void drawFooter(Object graphics, int mouseX, int mouseY) {
        int y = footerY();
        fillRoundRect(graphics, px, y, panelW, FOOTER_H, PANEL_R, 0xFF17110D);
        fillRect(graphics, px + PANEL_R, y, panelW - PANEL_R * 2, 1, C_BORDER);

        String notice = !statusMessage.isEmpty() ? statusMessage : (dirty ? "Unsaved changes" : "All changes saved");
        drawString(graphics, notice, px + 14, y + 17, dirty ? C_GOLD : C_TEXT_DIM);

        int saveX = px + panelW - 14 - 94;
        int discardX = saveX - 8 - 68;
        drawFooterButton(graphics, mouseX, mouseY, discardX, y + 9, 68, 24, "Discard", false);
        drawFooterButton(graphics, mouseX, mouseY, saveX, y + 9, 94, 24, "Save changes", true);
    }

    private void drawFooterButton(Object graphics, int mouseX, int mouseY, int x, int y,
                                  int w, int h, String text, boolean primary) {
        boolean hovered = hv(mouseX, mouseY, x, y, w, h);
        int fill = primary ? (hovered ? 0xFFF0A15B : C_GOLD) : (hovered ? C_HOVER : C_SURFACE);
        int textColor = primary ? 0xFF24170E : (hovered ? C_TEXT : C_TEXT_DIM);
        fillRoundRect(graphics, x, y, w, h, 7, fill);
        drawRoundBorder(graphics, x, y, w, h, 7, primary ? C_GOLD : C_BORDER);
        drawCenteredString(graphics, text, x + w / 2, y + 8, textColor);
    }

    private void drawScrollbar(Object graphics, int pageHeight) {
        int viewport = contentViewportH();
        if (pageHeight <= viewport) {
            return;
        }
        int x = contentRight() - 3;
        int y = contentTop();
        int thumbH = Math.max(24, viewport * viewport / pageHeight);
        int maxScroll = pageHeight - viewport;
        int thumbY = y + (int) ((viewport - thumbH) * scroll / maxScroll);
        fillRoundRect(graphics, x, y, 2, viewport, 1, 0x443F2C1D);
        fillRoundRect(graphics, x, thumbY, 2, thumbH, 1, C_GOLD_DARK);
    }

    private List<Setting> settingsForCurrentTab() {
        return settingPages.computeIfAbsent(tab, this::createSettingPage);
    }

    private List<Setting> settingsForDisplay() {
        if (searchQuery.isBlank()) {
            return settingsForCurrentTab();
        }
        List<Setting> matches = new ArrayList<>();
        for (int page = 0; page < 8; page++) {
            boolean first = true;
            for (Setting setting : settingPages.computeIfAbsent(page, this::createSettingPage)) {
                if (!matchesSearch(page, setting)) {
                    continue;
                }
                matches.add(new Setting(first ? "Search results · " + TAB_NAMES[page] : null,
                        setting.title(), setting.description(), setting.kind(), setting.index(), setting.chip()));
                first = false;
            }
        }
        return matches;
    }

    private boolean matchesSearch(int page, Setting setting) {
        String searchable = TAB_NAMES[page] + " " + setting.section() + " " + setting.title() + " " + setting.description() + " " + setting.chip();
        return searchable.toLowerCase(Locale.ROOT).contains(searchQuery);
    }

    private int pageFor(Setting setting) {
        for (int page = 0; page < 8; page++) {
            for (Setting candidate : settingPages.computeIfAbsent(page, this::createSettingPage)) {
                if (candidate.title().equals(setting.title()) && candidate.index() == setting.index()
                        && candidate.kind() == setting.kind()) {
                    return page;
                }
            }
        }
        return tab;
    }

    private final java.util.Map<Integer, List<Setting>> settingPages = new java.util.HashMap<>();

    private List<Setting> createSettingPage(int page) {
        return switch (page) {
            case 0 -> List.of(
                    setting("Studio", "Design Studio", "Interface, favorites, profiles, palettes and performance.", SettingKind.ACTION, -9, "OPEN"),
                    setting("General", "SkyCustomizer", "Enable all client-side SkyCustomizer features.", SettingKind.TOGGLE, 12),
                    setting("Level colors", "Level Colors", "Recolor SkyBlock levels in supported interfaces.", SettingKind.TOGGLE, 4),
                    setting(null, "Levels in Chat", "Apply your level colors to chat messages.", SettingKind.TOGGLE, 7),
                    setting("Quick access", "Scoreboard", "Enable the custom scoreboard appearance.", SettingKind.TOGGLE, 8),
                    setting(null, "Armor Colors", "Enable custom leather and armor tint colors.", SettingKind.TOGGLE, 9),
                    setting(null, "HUD Editor", "Drag and resize the scoreboard and actionbar with alignment guides.", SettingKind.ACTION, -2, "OPEN")
            );
            case 1 -> List.of(
                    setting("Identity", "Name Customizer", "Allow custom name formatting in supported locations.", SettingKind.TOGGLE, 0),
                    setting(null, "Use Custom Name", "Replace your displayed name with the value below.", SettingKind.TOGGLE, 1),
                    setting(null, "Custom Name", "Leave empty to keep your Minecraft username.", SettingKind.INPUT, 0),
                    setting("Format", "Prefix", "Text displayed before your custom name.", SettingKind.INPUT, 2),
                    setting(null, "Suffix", "Text displayed after your custom name.", SettingKind.INPUT, 3),
                    setting("Color and effects", "Name Color", "Hex color used for the custom name.", SettingKind.INPUT, 1),
                    setting(null, "Chroma", "Animate the name color with a smooth rainbow.", SettingKind.TOGGLE, 2),
                    setting(null, "Rainbow", "Use a different static rainbow color for each letter.", SettingKind.TOGGLE, 3),
                    setting("Full editor", "Nametag Editor", "Live preview, name styles, player aliases and tag visibility.", SettingKind.ACTION, -3, "OPEN")
            );
            case 2 -> List.of(
                    setting("Level colors", "Level Colors", "Enable custom colors for SkyBlock level prefixes.", SettingKind.TOGGLE, 4),
                    setting(null, "Override Hypixel Colors", "Also replace the original colors below level 480.", SettingKind.TOGGLE, 5),
                    setting(null, "Chroma at 600+", "Animate prestigious level colors from level 600 onward.", SettingKind.TOGGLE, 6),
                    setting(null, "Levels in Chat", "Show the selected level colors in chat.", SettingKind.TOGGLE, 7),
                    setting("Advanced", "Chroma Speed", "Speed multiplier for level chroma effects.", SettingKind.INPUT, 4),
                    setting(null, "Bracket Color", "Hex color for the brackets around level prefixes.", SettingKind.INPUT, 5),
                    setting("More colors", "Level Color Editor", "Edit every threshold with swatches, a color picker and live previews.", SettingKind.ACTION, -1, "OPEN")
            );
            case 3 -> List.of(
                    setting("Scoreboard", "Custom Scoreboard", "Enable the customized sidebar appearance.", SettingKind.TOGGLE, 8),
                    setting(null, "Scoreboard Title", "Replacement title for the Hypixel sidebar.", SettingKind.INPUT, 6),
                    setting(null, "Title Color", "Hex color used for the custom title.", SettingKind.INPUT, 7),
                    setting("More options", "Scoreboard Editor", "Choose the Screenshot preset or create your own sidebar style.", SettingKind.ACTION, -1, "OPEN"),
                    setting(null, "HUD Editor", "Move and resize your sidebar directly over the game.", SettingKind.ACTION, -2, "OPEN"),
                    setting(null, "Scoreboard Lines", "Edit hidden lines, replacements and extra sidebar text.", SettingKind.ACTION, -8, "OPEN")
            );
            case 4 -> List.of(
                    setting("Item appearance", "Item Editor", "Select an item, edit its name and lore, colors and enchantment shine.", SettingKind.ACTION, -4, "OPEN"),
                    setting("Commands", "Rename held item", "/sc itemname <name> • /sc itemlore <text> • /sc itemreset", SettingKind.INFO, -1, "HELD ITEM")
            );
            case 5 -> List.of(
                    setting("Armor colors", "Custom Armor Colors", "Enable the armor color replacement.", SettingKind.TOGGLE, 9),
                    setting(null, "Armor Chroma", "Animate the armor tint through rainbow colors.", SettingKind.TOGGLE, 10),
                    setting("Full editor", "Armor Editor", "Color pickers for each armor piece, global color and chroma.", SettingKind.ACTION, -5, "OPEN")
            );
            case 6 -> List.of(
                    setting("Chat", "Level Colors in Chat", "Apply SkyBlock level colors to supported chat messages.", SettingKind.TOGGLE, 7),
                    setting("Full editor", "Chat Editor", "Edit filters, highlights, timestamps and chat colors.", SettingKind.ACTION, -6, "OPEN")
            );
            case 7 -> List.of(
                    setting("HUD", "Custom HUD", "Enable SkyCustomizer HUD styling where supported.", SettingKind.TOGGLE, 11),
                    setting(null, "HUD Editor", "Drag, resize and align the scoreboard and actionbar.", SettingKind.ACTION, -2, "OPEN"),
                    setting("Full editor", "HUD & Text Editor", "Configure title colors, bossbar and actionbar appearance.", SettingKind.ACTION, -7, "OPEN")
            );
            default -> List.of();
        };
    }

    private static Setting setting(String section, String title, String description, SettingKind kind, int index) {
        return new Setting(section, title, description, kind, index, "");
    }

    private static Setting setting(String section, String title, String description,
                                   SettingKind kind, int index, String chip) {
        return new Setting(section, title, description, kind, index, chip);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean ignored) {
        if (event != null && event.button() == 0 && (hitSearch(event.x(), event.y()) || inputAt(event.x(), event.y()) >= 0)) {
            return super.mouseClicked(event, ignored);
        }
        if (event != null && handleMouseClicked(event.x(), event.y(), event.button())) {
            return true;
        }
        return super.mouseClicked(event, ignored);
    }

    public boolean handleMouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
        }
        int x = (int) mouseX;
        int y = (int) mouseY;

        int closeX = px + panelW - 34;
        if (hv(x, y, closeX, py + 15, 20, 20)) {
            onClose();
            return true;
        }

        int navX = navStart();
        int navSize = navSize();
        for (int i = 0; i < TAB_NAMES.length; i++) {
            if (hv(x, y, navX, py + 12, navSize, 28)) {
                tab = i;
                clearFocus();
                if (!searchQuery.isBlank()) {
                    search.setValue("");
                }
                scroll = 0;
                targetScroll = 0;
                return true;
            }
            navX += navSize + navGap();
        }

        int footerY = footerY();
        int saveX = px + panelW - 14 - 94;
        int discardX = saveX - 8 - 68;
        if (hv(x, y, saveX, footerY + 9, 94, 24)) {
            save();
            return true;
        }
        if (hv(x, y, discardX, footerY + 9, 68, 24)) {
            onClose();
            return true;
        }

        if (!hv(x, y, contentX(), contentTop(), contentW(), contentViewportH())) {
            clearFocus();
            return false;
        }
        return tab == 8 ? handlePresetClick(x, y) : handleSettingsClick(x, y);
    }

    private boolean handleSettingsClick(int mouseX, int mouseY) {
        int y = pageTop();
        for (Setting setting : settingsForDisplay()) {
            if (setting.section() != null) {
                y += 17;
            }
            if (hv(mouseX, mouseY, contentX(), y, contentW(), ROW_H)) {
                if (setting.kind() == SettingKind.TOGGLE) {
                    toggles[setting.index()] = !toggles[setting.index()];
                    dirty = true;
                } else if (setting.kind() == SettingKind.ACTION) {

                    save();
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft != null) {
                        if (setting.index() == -9) {
                            minecraft.setScreen(new WorkbenchScreen(this));
                        } else if (setting.index() <= -3) {
                            FeatureEditorScreen.Page page = switch (setting.index()) {
                                case -3 -> FeatureEditorScreen.Page.NAME;
                                case -4 -> FeatureEditorScreen.Page.ITEMS;
                                case -5 -> FeatureEditorScreen.Page.ARMOR;
                                case -6 -> FeatureEditorScreen.Page.CHAT;
                                case -7 -> FeatureEditorScreen.Page.VISUALS;
                                default -> FeatureEditorScreen.Page.SCOREBOARD;
                            };
                            minecraft.setScreen(new FeatureEditorScreen(this, page));
                        } else if (setting.index() == -2) {
                            minecraft.setScreen(new HudEditorScreen(this));
                        } else if (pageFor(setting) == 2) {
                            minecraft.setScreen(new LevelColorEditorScreen(this));
                        } else if (pageFor(setting) == 3) {
                            minecraft.setScreen(new ScoreboardEditorScreen(this));
                        }
                    }
                }
                return true;
            }
            y += ROW_H + ROW_GAP;
        }
        clearFocus();
        return false;
    }

    private boolean handlePresetClick(int mouseX, int mouseY) {
        int y = pageTop() + 17 + ROW_H + ROW_GAP + 10 + 17;
        for (String preset : cfg.presets.savedPresets) {
            if (hv(mouseX, mouseY, contentX(), y, contentW(), ROW_H)) {
                if (cfg.loadPreset(preset)) {
                    load();
                    dirty = true;
                    statusMessage = "Preset loaded: " + preset;
                    statusTimer = 70;
                } else {
                    statusMessage = "Preset not found: " + preset;
                    statusTimer = 70;
                }
                return true;
            }
            y += ROW_H + ROW_GAP;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (handleMouseScrolled(mouseX, mouseY, verticalAmount)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    public boolean handleMouseScrolled(double mouseX, double mouseY, double amount) {
        int max = Math.max(0, pageHeight() - contentViewportH());
        if (max == 0 || !hv((int) mouseX, (int) mouseY, contentX(), contentTop(), contentW(), contentViewportH())) {
            return false;
        }
        targetScroll = Math.max(0, Math.min(targetScroll - amount * 24, max));
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if(event!=null && (event.modifiers()&2)!=0 && (event.key()==90 || event.key()==89)) return super.keyPressed(event);
        if (event != null && handleKeyPressed(event.key(), event.scancode(), event.modifiers())) {
            return true;
        }
        return super.keyPressed(event);
    }

    public boolean handleKeyPressed(int key, int scanCode, int modifiers) {
        if (key == 256) {
            if (getFocused() instanceof EditBox) {
                clearFocus();
                return true;
            }
            onClose();
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        return super.charTyped(event);
    }

    @Override
    public void onClose() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int navStart() {
        return px + 57;
    }

    private int navSize() {
        int available = px + panelW - 42 - navStart();
        return Math.max(20, Math.min(28, (available - (TAB_NAMES.length - 1) * 2) / TAB_NAMES.length));
    }

    private int navGap() {
        int available = px + panelW - 42 - navStart();
        return Math.max(2, (available - TAB_NAMES.length * navSize()) / (TAB_NAMES.length - 1));
    }

    private int contentX() {
        return px + 11;
    }

    private int contentW() {
        return panelW - 22;
    }

    private int contentRight() {
        return contentX() + contentW();
    }

    private int contentTop() {
        return py + HEADER_H + 8;
    }

    private int searchX() {
        return px + 14;
    }

    private int searchY() {
        return py + 47;
    }

    private int searchW() {
        return panelW - 28;
    }

    private int footerY() {
        return py + panelH - FOOTER_H;
    }

    private int contentViewportH() {
        return footerY() - contentTop() - 5;
    }

    private int pageTop() {
        return contentTop() + 2 - (int) scroll;
    }

    private int controlW() {
        return Math.min(166, Math.max(90, contentW() / 3));
    }

    private int controlX() {
        return contentRight() - controlW() - 12;
    }

    private int pageHeight() {
        if (tab == 8 && searchQuery.isBlank()) {
            return 17 + ROW_H + ROW_GAP + 10 + 17
                    + Math.max(1, cfg.presets.savedPresets.size()) * (ROW_H + ROW_GAP) + 10;
        }
        int height = 2;
        for (Setting setting : settingsForDisplay()) {
            if (setting.section() != null) {
                height += 17;
            }
            height += ROW_H + ROW_GAP;
        }
        return height + 4;
    }

    private boolean visible(int y, int height) {
        return y + height > contentTop() && y < footerY();
    }

    private String fitting(String text, int maxWidth) {
        if (getStringWidth(text) <= maxWidth) {
            return text;
        }
        String shortened = text;
        while (shortened.length() > 1 && getStringWidth(shortened + "...") > maxWidth) {
            shortened = shortened.substring(0, shortened.length() - 1);
        }
        return shortened + "...";
    }

    private boolean hv(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private boolean hitSearch(double mouseX, double mouseY) {
        return hv((int) mouseX, (int) mouseY, searchX(), searchY(), searchW(), 24);
    }

    private int inputAt(double mouseX, double mouseY) {
        if (tab == 8 && searchQuery.isBlank()) {
            return -1;
        }
        int y = pageTop();
        for (Setting setting : settingsForDisplay()) {
            if (setting.section() != null) {
                y += 17;
            }
            if (setting.kind() == SettingKind.INPUT
                    && hv((int) mouseX, (int) mouseY, controlX(), y + 14, controlW(), 24)) {
                return setting.index();
            }
            y += ROW_H + ROW_GAP;
        }
        return -1;
    }
}
