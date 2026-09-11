package de.flori.skycustomizer.render;

import de.flori.skycustomizer.config.SkyCustomizerConfig;
import de.flori.skycustomizer.mixin.FontAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GlyphSource;
import net.minecraft.client.gui.font.glyphs.EffectGlyph;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;

public final class UiFonts {
    private static final FontDescription UI = new FontDescription.Resource(Identifier.fromNamespaceAndPath("skycustomizer", "ui"));
    private static Font source, menu;
    private UiFonts() {}
    public static Font font() {
        Font vanilla = Minecraft.getInstance().font;
        if (!(vanilla instanceof FontAccessor accessor)) return vanilla;
        if (source != vanilla) {
            source = vanilla;
            Font.Provider provider = accessor.skycustomizer$provider();
            menu = new Font(new Font.Provider() {
                @Override public GlyphSource glyphs(FontDescription requested) {
                    return provider.glyphs(SkyCustomizerConfig.get().ui.smoothText ? UI : requested);
                }
                @Override public EffectGlyph effect() { return provider.effect(); }
            });
        }
        return menu;
    }
}
