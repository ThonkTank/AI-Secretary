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
    public final int awardedXp;
    public final int comboPointDelta;

    public Occurrence(String id, TaskId taskId, LocalDate scheduledOn, OccurrenceState state,
                      int sortOrder, LocalDate completedOn) {
        this(id, taskId, scheduledOn, TaskSlot.MORNING, state, sortOrder, completedOn, 0, 0);
    }

    public Occurrence(String id, TaskId taskId, LocalDate scheduledOn, TaskSlot slot,
                      OccurrenceState state, int sortOrder, LocalDate completedOn) {
        this(id, taskId, scheduledOn, slot, state, sortOrder, completedOn, 0, 0);
    }

    public Occurrence(String id, TaskId taskId, LocalDate scheduledOn, TaskSlot slot,
                      OccurrenceState state, int sortOrder, LocalDate completedOn,
                      int awardedXp, int comboPointDelta) {
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
        this.awardedXp = Math.max(0, awardedXp);
        this.comboPointDelta = comboPointDelta;
    }

    public Occurrence complete(LocalDate date) {
        return new Occurrence(id, taskId, scheduledOn, slot,
                OccurrenceState.COMPLETED, sortOrder, date, awardedXp, comboPointDelta);
    }

    public Occurrence harvest(LocalDate date, int xp, int pointDelta) {
        return new Occurrence(id, taskId, scheduledOn, slot,
                OccurrenceState.COMPLETED, sortOrder, date, xp, pointDelta);
    }

    public Occurrence reopen() {
        return new Occurrence(id, taskId, scheduledOn, slot,
                OccurrenceState.OPEN, sortOrder, null, 0, 0);
    }

    public Occurrence moveTo(int newSortOrder) {
        return new Occurrence(id, taskId, scheduledOn, slot, state, newSortOrder, completedOn,
                awardedXp, comboPointDelta);
    }
}
