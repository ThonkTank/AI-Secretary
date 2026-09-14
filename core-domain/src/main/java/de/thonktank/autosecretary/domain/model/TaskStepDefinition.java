package de.thonktank.autosecretary.domain.model;

import java.util.Objects;

/** Validated, identity-optional step input. */
public final class TaskStepDefinition {
    public final String id;
    public final int position;
    public final String text;
    public final int weekdayMask;
    public final int intervalDays;
    public final StepPrescription prescription;
    public final String note;
    public final StepActivationKind activationKind;

    public TaskStepDefinition(String id, int position, String text, int weekdayMask,
                              int intervalDays, StepPrescription prescription,
                              String note,
                              StepActivationKind activationKind) {
        if (position < 0) throw new IllegalArgumentException("Step position must not be negative");
        if (text == null || text.trim().isEmpty())
            throw new IllegalArgumentException("Step title must not be blank");
        this.id = id == null || id.trim().isEmpty() ? null : id;
        this.position = position;
        this.text = text.trim();
        this.weekdayMask = weekdayMask & 0x7f;
        if (intervalDays != 0 && intervalDays < 2)
            throw new IllegalArgumentException("Step interval must be zero or at least two days");
        if (this.weekdayMask != 0 && intervalDays != 0)
            throw new IllegalArgumentException("Step weekdays and interval are mutually exclusive");
        this.intervalDays = intervalDays;
        this.prescription = Objects.requireNonNull(prescription, "prescription");
        this.note = note == null ? "" : note;
        this.activationKind = Objects.requireNonNull(activationKind, "activationKind");
    }

    public TaskStepDefinition withIdentity(String value, int newPosition) {
        return new TaskStepDefinition(value, newPosition, text, weekdayMask, intervalDays,
                prescription, note, activationKind);
    }

    @Override public boolean equals(Object other) {
        if (!(other instanceof TaskStepDefinition)) return false;
        TaskStepDefinition value = (TaskStepDefinition) other;
        return Objects.equals(id, value.id) && position == value.position
                && text.equals(value.text) && weekdayMask == value.weekdayMask
                && intervalDays == value.intervalDays && prescription.equals(value.prescription)
                && note.equals(value.note) && activationKind == value.activationKind;
    }

    @Override public int hashCode() {
        return Objects.hash(id, position, text, weekdayMask, intervalDays, prescription,
                note, activationKind);
    }
}
