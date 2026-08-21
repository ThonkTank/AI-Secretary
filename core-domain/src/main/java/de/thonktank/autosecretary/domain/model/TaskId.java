package de.thonktank.autosecretary.domain.model;

import java.util.Objects;

public final class TaskId {
    public final String value;

    private TaskId(String value) {
        this.value = value;
    }

    public static TaskId of(String value) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException("TaskId must not be blank");
        return new TaskId(value);
    }

    @Override public boolean equals(Object other) {
        return other instanceof TaskId && value.equals(((TaskId) other).value);
    }

    @Override public int hashCode() {
        return Objects.hash(value);
    }

    @Override public String toString() {
        return value;
    }
}
