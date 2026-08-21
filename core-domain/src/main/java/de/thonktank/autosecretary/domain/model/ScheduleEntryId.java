package de.thonktank.autosecretary.domain.model;

import java.util.Objects;

public final class ScheduleEntryId {
    public final String value;

    private ScheduleEntryId(String value) {
        if (value == null || value.trim().isEmpty())
            throw new IllegalArgumentException("Schedule entry id is required");
        this.value = value;
    }

    public static ScheduleEntryId of(String value) { return new ScheduleEntryId(value); }

    @Override public boolean equals(Object other) {
        return other instanceof ScheduleEntryId && value.equals(((ScheduleEntryId) other).value);
    }
    @Override public int hashCode() { return Objects.hash(value); }
}
