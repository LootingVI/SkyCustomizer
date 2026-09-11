# SkyCustomizer

Client-side customizer for Hypixel SkyBlock on Minecraft 26.1.2 / Fabric. It changes only local presentation: no packets, item data, outgoing chat messages or server-side player names are modified.

## Open the menu

- Press **K**, run `/skycustomizer`, or use the shorter alias `/sc`.
- Mod Menu opens the same configuration screen.
- The UI and command feedback are English.

## What is available

### Names, levels and chat

- Custom player names with prefix, suffix, colors and effects.
- Local aliases and configurable nametag rules for self, exact players and level ranges.
- Editable level-color thresholds, gradients and optional chroma at high levels.
- Chat type colors, highlights, local filters, timestamps and compact duplicate messages.

### Items and armor

- `/sc itemname <text>` changes the displayed name of the held item.
- `/sc itemlore <text>` adds local tooltip lines; use `\\n` for a new line.
- `/sc itemreset` clears these local changes for the held item.
- The Item Editor also supports saved item overrides, name/lore rules, rarity colors and hide/force enchantment shine.
- Leather armor color can be set per piece or globally, including chroma.

### Scoreboard and HUD

Open **Scoreboard → Scoreboard Editor**.

- Click the scoreboard title or a row to edit it in place. Color and bold formatting are supported.
- Add rows or spacers, reorder them, hide rows and reset fixed server-row replacements back to live values.
- The editor supports 15 visible rows in-game and can reveal hidden/overflow rows for editing.
- Import a PNG or JPEG as the scoreboard background through the file picker, drag-and-drop or a local path. Cover, Contain and Stretch, image opacity and darkening are available.
- Imported images are copied to `config/skycustomizer/backgrounds`; original files remain unchanged.
- **Save** commits the staged changes. **Discard** leaves earlier saved settings intact. Undo/redo includes the editor content and image settings.
- The HUD editor positions and scales the scoreboard and actionbar with snapping and alignment guides.

### Screenshots

![Configuration overview](docs/screenshots/config-overview.png)

![Direct scoreboard line editing](docs/screenshots/scoreboard-inline-editing.png)

![Custom scoreboard background image](docs/screenshots/scoreboard-background-image.png)

### Visuals

The active visual settings are custom title/subtitle colors, title chroma, title visibility, bossbar visibility, actionbar visibility and hiding the Minecraft vignette. HUD placement is handled by the HUD editor.

### Profiles and Design Studio

Profiles save and load complete configurations. The Design Studio manages interface animation, menu blur, color palettes, favorite settings, profile comparison and local frame-time measurements.

## Configuration compatibility

Old configuration files still load safely. The former global scoreboard-number option is migrated into the current scoreboard option. Removed placeholder settings are ignored when an older JSON file is read and are not written back on the next save.

## Build and verification

Requires Java 25.

```powershell
.\gradlew.bat build
.\gradlew.bat verifyHudLayout
.\gradlew.bat verifySourceUsage
.\gradlew.bat runUiSmoke
```

`build` runs the headless behavior checks and the source-usage check. The latter fails if a production method or field loses all references.

`runUiSmoke` starts an isolated development client in `build/ui-smoke-run`, tests the real menu hooks and exits automatically. Its test classes and test mod metadata are in `src/uiSmoke`; they are not packaged in the release JAR.

The release output is `build/libs/skycustomizer-1.0.0.jar`.
