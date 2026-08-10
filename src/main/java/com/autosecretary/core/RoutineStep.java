package com.autosecretary.core;

import java.time.DayOfWeek;
import java.util.EnumSet;
import java.util.Set;

/** One display-only step inside an atomically completed routine block. */
public final class RoutineStep {
    public String title;
    public Set<DayOfWeek> days;

    public RoutineStep(String title, Set<DayOfWeek> days) {
        this.title = title == null ? "" : title.trim();
        this.days = days == null || days.isEmpty()
                ? EnumSet.noneOf(DayOfWeek.class)
                : EnumSet.copyOf(days);
    }

    public boolean appliesOn(DayOfWeek day) {
        return days.isEmpty() || days.contains(day);
    }
}
