package de.thonktank.autosecretary.domain.model;

import java.time.LocalDate;

public final class Occurrence {
    public final String id;
    public final TaskId taskId;
    public final LocalDate scheduledOn;
    public final TaskSlot slot;
    public final OccurrenceState state;
    public final int sortOrder;
    public final LocalDate completedOn;
    public final OccurrenceKind kind;

    public Occurrence(String id, TaskId taskId, LocalDate scheduledOn, OccurrenceState state,
                      int sortOrder, LocalDate completedOn) {
        this(id, taskId, scheduledOn, TaskSlot.MORNING, state, sortOrder, completedOn,
                OccurrenceKind.SCHEDULED);
    }

    public Occurrence(String id, TaskId taskId, LocalDate scheduledOn, TaskSlot slot,
                      OccurrenceState state, int sortOrder, LocalDate completedOn) {
        this(id, taskId, scheduledOn, slot, state, sortOrder, completedOn,
                OccurrenceKind.SCHEDULED);
    }

    public Occurrence(String id, TaskId taskId, LocalDate scheduledOn, TaskSlot slot,
                      OccurrenceState state, int sortOrder, LocalDate completedOn,
                      OccurrenceKind kind) {
        if (id == null || id.trim().isEmpty() || taskId == null || scheduledOn == null
                || slot == null || state == null || kind == null)
            throw new IllegalArgumentException("Occurrence identity, task, date and state are required");
        if (state == OccurrenceState.COMPLETED && completedOn == null)
            throw new IllegalArgumentException("Completed occurrence needs a completion date");
        this.id = id;
        this.taskId = taskId;
        this.scheduledOn = scheduledOn;
        this.slot = slot;
        this.state = state;
        this.sortOrder = sortOrder;
        this.completedOn = completedOn;
        this.kind = kind;
    }

    public Occurrence complete(LocalDate date) {
        return new Occurrence(id, taskId, scheduledOn, slot,
                OccurrenceState.COMPLETED, sortOrder, date, kind);
    }

    public Occurrence missed() {
        if (state != OccurrenceState.OPEN)
            throw new IllegalStateException("Only an open occurrence can be missed");
        return new Occurrence(id, taskId, scheduledOn, slot,
                OccurrenceState.MISSED, sortOrder, null, kind);
    }

    public Occurrence reopen() {
        return new Occurrence(id, taskId, scheduledOn, slot,
                OccurrenceState.OPEN, sortOrder, null, kind);
    }

    public Occurrence moveTo(int newSortOrder) {
        return new Occurrence(id, taskId, scheduledOn, slot, state, newSortOrder, completedOn, kind);
    }
}
