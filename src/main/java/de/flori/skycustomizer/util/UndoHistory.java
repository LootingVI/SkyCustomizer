package de.flori.skycustomizer.util;

import java.util.*;

public final class UndoHistory<T> {
    private final List<T> states = new ArrayList<>();
    private int cursor = -1;
    private long last;
    public void reset(T state) { states.clear(); states.add(state); cursor = 0; last = 0; }
    public void record(T state, long now, boolean typing) {
        if (cursor < 0) { reset(state); return; }
        if (Objects.equals(states.get(cursor), state)) return;
        while (states.size() > cursor + 1) states.removeLast();
        if (typing && cursor > 0 && now - last < 500_000_000L) states.set(cursor, state);
        else { states.add(state); cursor++; }
        if (states.size() > 100) { states.removeFirst(); cursor--; }
        last = now;
    }
    public T undo() { last = 0; return states.get(cursor > 0 ? --cursor : cursor); }
    public T redo() { last = 0; return states.get(cursor + 1 < states.size() ? ++cursor : cursor); }
    public boolean canUndo() { return cursor > 0; }
    public boolean canRedo() { return cursor + 1 < states.size(); }
}
