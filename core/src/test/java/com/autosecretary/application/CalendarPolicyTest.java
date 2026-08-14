package com.autosecretary.application;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

public final class CalendarPolicyTest {
    private static final Instant START = Instant.parse("2026-08-14T08:00:00Z");
    private static final Instant END = Instant.parse("2026-08-14T09:00:00Z");

    @Test
    public void excludesAllDayFreeCanceledDeclinedAndInvisibleButKeepsTentative() {
        CalendarPolicy policy = new CalendarPolicy();
        List<CalendarOccurrence> values = List.of(
                value(1, true, CalendarAvailability.BUSY, CalendarStatus.CONFIRMED,
                        CalendarParticipation.ACCEPTED, CalendarVisibility.VISIBLE),
                value(2, false, CalendarAvailability.FREE, CalendarStatus.CONFIRMED,
                        CalendarParticipation.ACCEPTED, CalendarVisibility.VISIBLE),
                value(3, false, CalendarAvailability.BUSY, CalendarStatus.CANCELED,
                        CalendarParticipation.ACCEPTED, CalendarVisibility.VISIBLE),
                value(4, false, CalendarAvailability.BUSY, CalendarStatus.CONFIRMED,
                        CalendarParticipation.DECLINED, CalendarVisibility.VISIBLE),
                value(5, false, CalendarAvailability.BUSY, CalendarStatus.CONFIRMED,
                        CalendarParticipation.ACCEPTED, CalendarVisibility.HIDDEN),
                value(6, false, CalendarAvailability.TENTATIVE, CalendarStatus.TENTATIVE,
                        CalendarParticipation.TENTATIVE, CalendarVisibility.TITLE_HIDDEN));

        var relevant = policy.relevantOccurrences(values);

        assertEquals(List.of(6L), relevant.stream().map(value -> value.id().eventId()).toList());
        assertNull(policy.busyIntervals(relevant, ZoneId.of("Europe/Berlin")).get(0).title());
    }

    private static CalendarOccurrence value(
            long id, boolean allDay, CalendarAvailability availability, CalendarStatus status,
            CalendarParticipation participation, CalendarVisibility visibility) {
        return new CalendarOccurrence(new CalendarOccurrenceId(1, id, START), START, END,
                allDay, availability, status, participation, visibility,
                visibility == CalendarVisibility.VISIBLE ? Optional.of("Termin") : Optional.empty());
    }
}
