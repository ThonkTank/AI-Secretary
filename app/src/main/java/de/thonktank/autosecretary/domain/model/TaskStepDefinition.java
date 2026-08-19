package de.thonktank.autosecretary.domain.model;

import java.util.Objects;

public final class TaskStepDefinition {
    public final String id;
    public final int position;
    public final String text;
    public final int weekdayMask;
    public final StepAmount amount;
    public final String note;

    public TaskStepDefinition(String id, int position, String text, int weekdayMask,
                              StepAmount amount, String note) {
        if (position < 0) throw new IllegalArgumentException("Step position must not be negative");
        if (text == null || text.trim().isEmpty())
            throw new IllegalArgumentException("Step title must not be blank");
        this.id = id == null || id.trim().isEmpty() ? null : id;
        this.position = position;
        this.text = text.trim();
        this.weekdayMask = weekdayMask & 0x7f;
        this.amount = StepAmount.requireValid(amount);
        this.note = note == null ? "" : note;
    }

    public TaskStepDefinition withIdentity(String value, int newPosition) {
        return new TaskStepDefinition(value, newPosition, text, weekdayMask, amount, note);
    }

    @Override public boolean equals(Object other) {
        if (!(other instanceof TaskStepDefinition)) return false;
        TaskStepDefinition value = (TaskStepDefinition) other;
        return Objects.equals(id, value.id) && position == value.position
                && text.equals(value.text) && weekdayMask == value.weekdayMask
                && amount.equals(value.amount) && note.equals(value.note);
    }

    @Override public int hashCode() {
        return Objects.hash(id, position, text, weekdayMask, amount, note);
    }
}
