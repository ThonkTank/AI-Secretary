package com.autosecretary.application;

import com.autosecretary.domain.PlanAssignment;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Stream;

/** The single query that defines which remaining entries belong to Today. */
public final class GetTodayTimeline {
    private final TimeProvider time;
    private final CalendarPolicy calendarPolicy;

    public GetTodayTimeline(TimeProvider time) {
        this(time, new CalendarPolicy());
    }

    public GetTodayTimeline(TimeProvider time, CalendarPolicy calendarPolicy) {
        this.time = time;
        this.calendarPolicy = calendarPolicy;
    }

    public TodayTimeline execute(DashboardData dashboard) {
        Instant now = time.now();
        ZoneId zone = time.zone();
        LocalDate day = now.atZone(zone).toLocalDate();
        Instant dayStart = day.atStartOfDay(zone).toInstant();
        Instant dayEnd = day.plusDays(1).atStartOfDay(zone).toInstant();
        List<CalendarOccurrence> relevantCalendar = calendarPolicy.relevantOccurrences(
                dashboard.calendarOccurrences());
        List<TodayEntry> entries = new ArrayList<>();
        dashboard.focus().stream()
                .filter(value -> value.start().toLocalDate().equals(day))
                .map(value -> focus(value, relevantCalendar, zone))
                .filter(value -> value.end().isAfter(now))
                .forEach(entries::add);
        relevantCalendar.stream()
                .filter(value -> value.start().isBefore(dayEnd))
                .filter(value -> value.end().isAfter(dayStart))
                .filter(value -> value.end().isAfter(now))
                .map(TodayEntry.Calendar::new)
                .forEach(entries::add);
        entries.sort(Comparator.comparing(TodayEntry::start)
                .thenComparingInt(value -> value instanceof TodayEntry.Calendar ? 0 : 1)
                .thenComparing(TodayEntry::stableId));
        LinkedHashMap<String, TodayEntry> unique = new LinkedHashMap<>();
        entries.forEach(value -> unique.putIfAbsent(value.stableId(), value));
        List<TodayEntry> deduplicated = List.copyOf(unique.values());
        Instant nextRefresh = Stream.concat(Stream.of(dayEnd), deduplicated.stream()
                        .flatMap(value -> Stream.of(value.start(), value.end())))
                .filter(value -> value.isAfter(now))
                .min(Instant::compareTo).orElse(dayEnd);
        return new TodayTimeline(day, now, nextRefresh, deduplicated,
                dashboard.undoLabel() != null && !dashboard.undoLabel().isBlank());
    }

    private static TodayEntry.Focus focus(
            PlanAssignment assignment,
            List<CalendarOccurrence> calendar,
            ZoneId zone) {
        Instant start = assignment.start().atZone(zone).toInstant();
        Instant end = assignment.end().atZone(zone).toInstant();
        CalendarOccurrence preceding = calendar.stream()
                .filter(value -> !value.end().isAfter(start))
                .filter(value -> value.end().atZone(zone).toLocalDate()
                        .equals(assignment.start().toLocalDate()))
                .max(Comparator.comparing(CalendarOccurrence::end)
                        .thenComparing(CalendarOccurrence::start)
                        .thenComparing(value -> value.id().stableValue()))
                .orElse(null);
        return new TodayEntry.Focus(assignment, preceding, start, end);
    }
}
