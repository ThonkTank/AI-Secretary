package com.autosecretary.core;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/** One independently completable child task inside a routine block. */
public final class RoutineStep {
    public String id;
    public String title;
    public Set<DayOfWeek> days;
    public LocalDate completedFor;
    public LocalDateTime completedAt;

    public RoutineStep(String title, Set<DayOfWeek> days) {
        this(UUID.randomUUID().toString(), title, days, null, null);
    }

    public RoutineStep(
            String id,
            String title,
            Set<DayOfWeek> days,
            LocalDate completedFor,
            LocalDateTime completedAt) {
        this.id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
        this.title = title == null ? "" : title.trim();
        this.days = days == null || days.isEmpty()
                ? EnumSet.noneOf(DayOfWeek.class)
                : EnumSet.copyOf(days);
        this.completedFor = completedFor;
        this.completedAt = completedAt;
    }

    public boolean appliesOn(DayOfWeek day) {
        return days.isEmpty() || days.contains(day);
    }

    public boolean isCompletedFor(LocalDate occurrenceDate) {
        return occurrenceDate != null && occurrenceDate.equals(completedFor);
    }

    public void setCompletedFor(LocalDate occurrenceDate, boolean completed, LocalDateTime at) {
        completedFor = completed ? occurrenceDate : null;
        completedAt = completed ? at : null;
    }

    public RoutineStep copy() {
        return new RoutineStep(id, title, days, completedFor, completedAt);
    }
}
