package de.flori.skycustomizer.config;

import de.flori.skycustomizer.render.UiFonts;
import de.flori.skycustomizer.util.UndoHistory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

public abstract class StudioScreen extends Screen {
    private final UndoHistory<String> history = new UndoHistory<>();
    private boolean historyReady;
    private long sampledAt;
    protected StudioScreen(String title) { super(Minecraft.getInstance(), UiFonts.font(), Component.literal(title)); }
    protected String[] historyFields() { return new String[0]; }
    protected String captureDraft() { return historyFields().length == 0 ? null : DraftSnapshots.capture(this, historyFields()); }
    protected void restoreDraft(String state) { DraftSnapshots.restore(this, state, historyFields()); afterHistory(); }
    protected void afterHistory() {}
    protected final void startHistory() {
        if (!historyReady) { String state = captureDraft(); if (state != null) { history.reset(state); historyReady = true; } }
    }
    protected final void checkpoint() { startHistory(); if (historyReady) history.record(captureDraft(), System.nanoTime(), false); }
    protected final void undo(boolean redo) {
        checkpoint();
        if (historyReady && (redo ? history.canRedo() : history.canUndo())) restoreDraft(redo ? history.redo() : history.undo());
    }
    @Override public void tick() {
        super.tick(); startHistory();
        long now=System.nanoTime();
        if (historyReady && now-sampledAt>=200_000_000L) { sampledAt=now;history.record(captureDraft(), now, getFocused() instanceof net.minecraft.client.gui.components.EditBox); }
    }
    @Override public boolean keyPressed(KeyEvent e) {
        if ((e.modifiers() & 2) != 0 && (e.key() == 90 || e.key() == 89)) {
            if (getFocused() instanceof net.minecraft.client.gui.components.EditBox) return super.keyPressed(e);
            undo(e.key() == 89 || (e.modifiers() & 1) != 0); return true;
        }
        return super.keyPressed(e);
    }
    protected void backdrop(GuiGraphicsExtractor g, float delta) {
        if (minecraft.level == null) extractPanorama(g, delta);
        if (SkyCustomizerConfig.get().ui.backgroundBlur) g.blurBeforeThisStratum();
    }
    @Override public void extractBackground(GuiGraphicsExtractor g, int x, int y, float delta) {}
    @Override public boolean isPauseScreen() { return false; }
}
