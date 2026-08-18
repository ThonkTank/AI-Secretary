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

    public Occurrence(String id, TaskId taskId, LocalDate scheduledOn, OccurrenceState state,
                      int sortOrder, LocalDate completedOn) {
        this(id, taskId, scheduledOn, TaskSlot.MORNING, state, sortOrder, completedOn);
    }

    public Occurrence(String id, TaskId taskId, LocalDate scheduledOn, TaskSlot slot,
                      OccurrenceState state, int sortOrder, LocalDate completedOn) {
        if (id == null || id.trim().isEmpty() || taskId == null || scheduledOn == null
                || slot == null || state == null)
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
    }

    public Occurrence complete(LocalDate date) {
        return new Occurrence(id, taskId, scheduledOn, slot,
                OccurrenceState.COMPLETED, sortOrder, date);
    }

    public Occurrence reopen() {
        return new Occurrence(id, taskId, scheduledOn, slot,
                OccurrenceState.OPEN, sortOrder, null);
    }

    public Occurrence moveTo(int newSortOrder) {
        return new Occurrence(id, taskId, scheduledOn, slot, state, newSortOrder, completedOn);
    }
}
