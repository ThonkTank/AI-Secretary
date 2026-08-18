package de.thonktank.autosecretary;

import android.view.View;

import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Explicit registry replacing string View tags and hierarchy searches. */
public final class RewardAnchorRegistry {
    private final Map<RewardAnchorKey, WeakReference<View>> anchors = new LinkedHashMap<>();

    public synchronized void register(RewardAnchorKey key, View view) {
        if (key != null && view != null) anchors.put(key, new WeakReference<>(view));
        prune();
    }

    public synchronized View find(RewardAnchorKey key) {
        WeakReference<View> reference = anchors.get(key);
        View view = reference == null ? null : reference.get();
        return view != null && view.getVisibility() == View.VISIBLE ? view : null;
    }

    public synchronized View firstVisible(RewardAnchorKey.Kind kind) {
        prune();
        for (Map.Entry<RewardAnchorKey, WeakReference<View>> entry : anchors.entrySet()) {
            View view = entry.getValue().get();
            if (entry.getKey().kind == kind && view != null
                    && view.getVisibility() == View.VISIBLE) return view;
        }
        return null;
    }

    public synchronized void clearDynamic() {
        anchors.keySet().removeIf(key -> key.kind != RewardAnchorKey.Kind.HEAD);
    }

    private void prune() {
        Iterator<WeakReference<View>> values = anchors.values().iterator();
        while (values.hasNext()) if (values.next().get() == null) values.remove();
    }
}
