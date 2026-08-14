package com.autosecretary.application;

import java.time.Instant;
import java.util.Optional;

/** Lossless, Android-independent representation of an expanded calendar occurrence. */
public record CalendarOccurrence(
        CalendarOccurrenceId id,
        Instant start,
        Instant end,
        boolean allDay,
        CalendarAvailability availability,
        CalendarStatus status,
        CalendarParticipation participation,
        CalendarVisibility visibility,
        Optional<String> title) {
    public CalendarOccurrence {
        if (id == null || start == null || end == null || !end.isAfter(start)) {
            throw new IllegalArgumentException("Ungültiger Kalendertermin");
        }
        availability = availability == null ? CalendarAvailability.UNKNOWN : availability;
        status = status == null ? CalendarStatus.UNKNOWN : status;
        participation = participation == null ? CalendarParticipation.UNKNOWN : participation;
        visibility = visibility == null ? CalendarVisibility.TITLE_HIDDEN : visibility;
        title = title == null ? Optional.empty()
                : title.filter(value -> !value.isBlank());
        if (visibility != CalendarVisibility.VISIBLE) title = Optional.empty();
    }
}
