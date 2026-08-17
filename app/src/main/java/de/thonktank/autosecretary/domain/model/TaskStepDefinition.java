package de.thonktank.autosecretary.domain.model;

import java.util.Objects;

public final class TaskStepDefinition {
    public final String id;
    public final int position;
    public final String text;
    public final int weekdayMask;
    public final StepAmountKind amountKind;
    public final Integer plannedSets;
    public final Integer plannedReps;
    public final Integer plannedDurationSeconds;
    public final String note;

    public TaskStepDefinition(String id, int position, String text, int weekdayMask,
                              StepAmountKind amountKind, Integer plannedSets,
                              Integer plannedReps, Integer plannedDurationSeconds, String note) {
        if (position < 0) throw new IllegalArgumentException("Step position must not be negative");
        if (text == null || text.trim().isEmpty())
            throw new IllegalArgumentException("Step title must not be blank");
        if (amountKind == null) throw new IllegalArgumentException("Step amount kind is required");
        Integer sets = null;
        Integer reps = null;
        Integer duration = null;
        if (amountKind == StepAmountKind.SETS_REPS) {
            sets = positive(plannedSets, "sets");
            reps = positive(plannedReps, "repetitions");
        } else if (amountKind == StepAmountKind.REPS) {
            reps = positive(plannedReps, "repetitions");
        } else if (amountKind == StepAmountKind.DURATION) {
            duration = positive(plannedDurationSeconds, "duration");
        }
        this.id = id == null || id.trim().isEmpty() ? null : id;
        this.position = position;
        this.text = text.trim();
        this.weekdayMask = weekdayMask & 0x7f;
        this.amountKind = amountKind;
        this.plannedSets = sets;
        this.plannedReps = reps;
        this.plannedDurationSeconds = duration;
        this.note = note == null ? "" : note;
    }

    public TaskStepDefinition withIdentity(String value, int newPosition) {
        return new TaskStepDefinition(value, newPosition, text, weekdayMask, amountKind,
                plannedSets, plannedReps, plannedDurationSeconds, note);
    }

    private static int positive(Integer value, String name) {
        if (value == null || value <= 0)
            throw new IllegalArgumentException("Step " + name + " must be positive");
        return value;
    }

    @Override public boolean equals(Object other) {
        if (!(other instanceof TaskStepDefinition)) return false;
        TaskStepDefinition value = (TaskStepDefinition) other;
        return Objects.equals(id, value.id) && position == value.position
                && text.equals(value.text) && weekdayMask == value.weekdayMask
                && amountKind == value.amountKind && Objects.equals(plannedSets, value.plannedSets)
                && Objects.equals(plannedReps, value.plannedReps)
                && Objects.equals(plannedDurationSeconds, value.plannedDurationSeconds)
                && note.equals(value.note);
    }

    @Override public int hashCode() {
        return Objects.hash(id, position, text, weekdayMask, amountKind, plannedSets,
                plannedReps, plannedDurationSeconds, note);
    }
}
