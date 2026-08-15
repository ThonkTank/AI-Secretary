package de.thonktank.autosecretary.calendar;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

public final class CalendarDayWindow {
    public final LocalDate date;
    public final ZoneId zone;
    public final Instant begin;
    public final Instant endExclusive;

    private CalendarDayWindow(LocalDate date, ZoneId zone, Instant begin, Instant endExclusive) {
        this.date = date;
        this.zone = zone;
        this.begin = begin;
        this.endExclusive = endExclusive;
    }

    public static CalendarDayWindow of(LocalDate date, ZoneId zone) {
        return new CalendarDayWindow(date, zone, date.atStartOfDay(zone).toInstant(),
                date.plusDays(1).atStartOfDay(zone).toInstant());
    }
}
