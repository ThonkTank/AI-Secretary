package de.thonktank.autosecretary.domain.model;

import java.time.LocalDate;

/** Presentation position only: never changes occurrence due dates or flow identity. */
public final class TodayPlacement {
    public enum Kind { OCCURRENCE, FLOW_TASK_SHEET, TASK }
    public final Kind kind;
    public final String id;
    public final LocalDate displayOn;
    public final TaskSlot slot;
    public final int position;

    public TodayPlacement(Kind kind, String id, LocalDate displayOn, TaskSlot slot, int position) {
        if (kind == null || id == null || id.isEmpty() || displayOn == null || slot == null || position < 0)
            throw new IllegalArgumentException("Today placement is incomplete");
        this.kind = kind; this.id = id; this.displayOn = displayOn;
        this.slot = slot; this.position = position;
    }
    public String key() { return kind.name() + ":" + id; }
}
