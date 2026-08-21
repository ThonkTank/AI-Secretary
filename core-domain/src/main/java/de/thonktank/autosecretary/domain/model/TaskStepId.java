package de.thonktank.autosecretary.domain.model;

import java.util.Objects;

public final class TaskStepId {
    public final String value;

    private TaskStepId(String value) {
        if (value == null || value.trim().isEmpty())
            throw new IllegalArgumentException("Task step id is required");
        this.value = value;
    }

    public static TaskStepId of(String value) { return new TaskStepId(value); }

    @Override public boolean equals(Object other) {
        return other instanceof TaskStepId && value.equals(((TaskStepId) other).value);
    }
    @Override public int hashCode() { return Objects.hash(value); }
}
