package de.thonktank.autosecretary.ui.leaf;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Access-ordered cache bounded by estimated path memory instead of entry count. */
final class WoodGrainRenderCache {
    private final int maximumBytes;
    private final LinkedHashMap<String, WoodGrainRenderData> values =
            new LinkedHashMap<>(16, .75f, true);
    private int bytes;

    WoodGrainRenderCache(int maximumBytes) {
        this.maximumBytes = Math.max(1, maximumBytes);
    }

    synchronized WoodGrainRenderData get(String key) { return values.get(key); }

    synchronized void put(String key, WoodGrainRenderData data) {
        WoodGrainRenderData previous = values.remove(key);
        if (previous != null) bytes -= previous.estimatedBytes;
        if (data.estimatedBytes > maximumBytes) return;
        values.put(key, data);
        bytes += data.estimatedBytes;
        Iterator<Map.Entry<String, WoodGrainRenderData>> iterator =
                values.entrySet().iterator();
        while (bytes > maximumBytes && iterator.hasNext()) {
            bytes -= iterator.next().getValue().estimatedBytes;
            iterator.remove();
        }
    }

    synchronized void clear() { values.clear(); bytes = 0; }
    synchronized int bytes() { return bytes; }
    synchronized int size() { return values.size(); }
    int maximumBytes() { return maximumBytes; }
}
