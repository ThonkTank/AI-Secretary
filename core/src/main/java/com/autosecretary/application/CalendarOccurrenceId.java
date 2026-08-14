package com.autosecretary.application;

import java.time.Instant;

/** Stable identity of one expanded calendar instance. */
public record CalendarOccurrenceId(long calendarId, long eventId, Instant instanceStart) {
    public CalendarOccurrenceId {
        if (instanceStart == null) throw new IllegalArgumentException("Instanzbeginn fehlt");
    }

    public String stableValue() {
        return calendarId + ":" + eventId + ":" + instanceStart;
    }
}
