package de.thonktank.autosecretary.domain.model;

import java.time.LocalDate;

/** One genuine scheduled task or step obligation. Carry-forward never creates another row. */
public final class ComboObligation {
    public enum State { OPEN, RESOLVED }

    public final String id;
    public final String ownerId;
    public final TaskId taskId;
    public final ComboProgress.Kind kind;
    public final TaskSlot slot;
    public final LocalDate scheduledOn;
    public final String occurrenceId;
    public final State state;
    public final LocalDate resolvedOn;

    public ComboObligation(String id, String ownerId, TaskId taskId, ComboProgress.Kind kind,
                           TaskSlot slot, LocalDate scheduledOn, String occurrenceId,
                           State state, LocalDate resolvedOn) {
        if (blank(id) || blank(ownerId) || taskId == null || kind == null || slot == null
                || scheduledOn == null || blank(occurrenceId) || state == null)
            throw new IllegalArgumentException("Combo obligation identity and schedule are required");
        if (state == State.OPEN && resolvedOn != null)
            throw new IllegalArgumentException("Open combo obligation cannot have a resolution date");
        if (state == State.RESOLVED && resolvedOn == null)
            throw new IllegalArgumentException("Resolved combo obligation needs a date");
        this.id = id;
        this.ownerId = ownerId;
        this.taskId = taskId;
        this.kind = kind;
        this.slot = slot;
        this.scheduledOn = scheduledOn;
        this.occurrenceId = occurrenceId;
        this.state = state;
        this.resolvedOn = resolvedOn;
    }

    public static ComboObligation open(String ownerId, TaskId taskId, ComboProgress.Kind kind,
                                       TaskSlot slot, LocalDate scheduledOn,
                                       String occurrenceId) {
        return new ComboObligation(id(ownerId, slot, scheduledOn), ownerId, taskId, kind,
                slot, scheduledOn, occurrenceId, State.OPEN, null);
    }

    public ComboObligation resolve(LocalDate date) {
        return state == State.RESOLVED ? this : new ComboObligation(id, ownerId, taskId, kind,
                slot, scheduledOn, occurrenceId, State.RESOLVED, date);
    }

    public ComboObligation reopen() {
        return state == State.OPEN ? this : new ComboObligation(id, ownerId, taskId, kind,
                slot, scheduledOn, occurrenceId, State.OPEN, null);
    }

    public static String id(String ownerId, TaskSlot slot, LocalDate scheduledOn) {
        return ownerId + '|' + slot.name() + '|' + scheduledOn;
    }

    private static boolean blank(String value) { return value == null || value.trim().isEmpty(); }
}
