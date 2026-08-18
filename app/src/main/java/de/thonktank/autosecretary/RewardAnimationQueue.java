package de.thonktank.autosecretary;

import java.util.ArrayDeque;

/** FIFO gate guaranteeing that at most one reward motion is active. */
final class RewardAnimationQueue {
    private final ArrayDeque<UiEvent> pending = new ArrayDeque<>();
    private boolean active;

    void offer(UiEvent event) { if (event != null) pending.addLast(event); }

    UiEvent startNext() {
        if (active || pending.isEmpty()) return null;
        active = true;
        return pending.removeFirst();
    }

    void finish() { active = false; }

    int size() { return pending.size() + (active ? 1 : 0); }
    boolean isActive() { return active; }
}
