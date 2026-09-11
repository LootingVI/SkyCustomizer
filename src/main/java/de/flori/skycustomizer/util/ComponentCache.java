package de.flori.skycustomizer.util;

import net.minecraft.network.chat.Component;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ComponentCache {
    private record Key(Component text, long revision, long frame) {}
    private final Map<Key, Component> entries = new LinkedHashMap<>(256, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Key, Component> e) { return size() > 512; }
    };
    public synchronized Component get(Component source, long revision, long frame) {
        Component value = entries.get(new Key(source, revision, frame));
        return value == null ? null : copyTree(value);
    }
    public synchronized void put(Component source, Component result, long revision, long frame) {
        entries.put(new Key(copyTree(source), revision, frame), copyTree(result));
    }
    private static Component copyTree(Component source) {
        var copy = source.copy();
        copy.getSiblings().clear();
        for (Component child : source.getSiblings()) copy.append(copyTree(child));
        return copy;
    }
}
