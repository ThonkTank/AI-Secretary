package com.autosecretary.domain;

import java.time.LocalTime;

/** User-controlled planner boundaries and buffers. */
public record PlanningSettings(
        TimeWindow day,
        TimeWindow morning,
        TimeWindow midday,
        TimeWindow evening,
        int taskTransitionMinutes,
        int calendarBufferBeforeMinutes,
        int calendarBufferAfterMinutes,
        int horizonDays) {

    public PlanningSettings {
        if (day == null || morning == null || midday == null || evening == null) {
            throw new IllegalArgumentException("Planungsfenster fehlen");
        }
        if (!inside(day, morning) || !inside(day, midday) || !inside(day, evening)) {
            throw new IllegalArgumentException("Zeitpräferenzen müssen im Planungstag liegen");
        }
        if (taskTransitionMinutes < 0 || calendarBufferBeforeMinutes < 0
                || calendarBufferAfterMinutes < 0) {
            throw new IllegalArgumentException("Puffer dürfen nicht negativ sein");
        }
        if (horizonDays < 1 || horizonDays > 14) {
            throw new IllegalArgumentException("Planungshorizont muss 1–14 Tage umfassen");
        }
    }

    public static PlanningSettings defaults() {
        return new PlanningSettings(
                new TimeWindow(LocalTime.of(7, 0), LocalTime.of(22, 0)),
                new TimeWindow(LocalTime.of(7, 0), LocalTime.of(11, 0)),
                new TimeWindow(LocalTime.of(11, 0), LocalTime.of(15, 0)),
                new TimeWindow(LocalTime.of(17, 0), LocalTime.of(22, 0)),
                15, 15, 15, 7);
    }

    public TimeWindow preferenceWindow(TimePreference preference) {
        if (preference == null) return day;
        return switch (preference) {
            case MORNING -> morning;
            case MIDDAY -> midday;
            case EVENING -> evening;
        };
    }

    private static boolean inside(TimeWindow outer, TimeWindow inner) {
        return !inner.start().isBefore(outer.start()) && !inner.end().isAfter(outer.end());
    }
}
