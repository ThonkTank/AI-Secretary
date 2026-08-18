package de.thonktank.autosecretary;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

/** ID-based FIFO queue. Effects remain pending until the renderer acknowledges them. */
public final class RewardEffectQueue {
    private final LinkedHashMap<String, RewardEffect> pending = new LinkedHashMap<>();

    public synchronized Snapshot enqueue(RewardEffect effect) {
        if (effect != null) pending.putIfAbsent(effect.id, effect);
        return snapshot();
    }

    public synchronized Snapshot acknowledge(String id) {
        if (id != null) pending.remove(id);
        return snapshot();
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(new ArrayList<>(pending.values()));
    }

    public static final class Snapshot {
        public final List<RewardEffect> pending;
        Snapshot(List<RewardEffect> pending) {
            this.pending = Collections.unmodifiableList(pending);
        }
        public RewardEffect first() { return pending.isEmpty() ? null : pending.get(0); }
    }
}
