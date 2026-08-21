package de.thonktank.autosecretary.domain.model;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Persistent, per-element combo account. The visible level is always derived. */
public final class ComboProgress {
    public enum Kind { TASK, STEP }

    public final String ownerId;
    public final TaskId taskId;
    public final Kind kind;
    public final int points;
    public final LocalDate settledThroughOn;

    public ComboProgress(String ownerId, TaskId taskId, Kind kind, int points,
                         LocalDate settledThroughOn) {
        if (ownerId == null || ownerId.isEmpty() || taskId == null || kind == null)
            throw new IllegalArgumentException("Combo owner, task and kind are required");
        this.ownerId = ownerId;
        this.taskId = taskId;
        this.kind = kind;
        this.points = Math.max(0, points);
        this.settledThroughOn = settledThroughOn;
    }

    public static ComboProgress fresh(String ownerId, TaskId taskId, Kind kind) {
        return new ComboProgress(ownerId, taskId, kind, 0, null);
    }

    public static String taskOwner(TaskId taskId) { return "task:" + taskId.value; }
    public static String stepOwner(String templateId) { return "step:" + templateId; }

    public int level() {
        return (int) Math.floor((Math.sqrt(8d * points + 1d) - 1d) / 2d);
    }

    public double multiplier() { return 1d + level() * .5d; }

    /** Settles fully elapsed local calendar days. A new account does not decay. */
    public ComboProgress settle(LocalDate today) {
        if (today == null || settledThroughOn == null) return this;
        LocalDate target = today.minusDays(1);
        if (!target.isAfter(settledThroughOn)) return this;
        long days = ChronoUnit.DAYS.between(settledThroughOn, target);
        return new ComboProgress(ownerId, taskId, kind,
                Math.max(0, points - Math.toIntExact(Math.min(Integer.MAX_VALUE, days * 2L))),
                target);
    }

    public Change change(int requestedDelta, LocalDate today) {
        ComboProgress settled = settle(today);
        int next = Math.max(0, settled.points + requestedDelta);
        int applied = next - settled.points;
        return new Change(new ComboProgress(ownerId, taskId, kind, next, today), applied);
    }

    public ComboProgress undo(int appliedDelta, LocalDate today) {
        ComboProgress settled = settle(today);
        return new ComboProgress(ownerId, taskId, kind,
                Math.max(0, settled.points - appliedDelta), today);
    }

    public static final class Change {
        public final ComboProgress progress;
        public final int appliedDelta;
        Change(ComboProgress progress, int appliedDelta) {
            this.progress = progress;
            this.appliedDelta = appliedDelta;
        }
    }
}
