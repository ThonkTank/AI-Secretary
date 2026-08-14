package com.autosecretary.application;

import com.autosecretary.domain.BusyInterval;
import com.autosecretary.domain.PlanAssignment;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Stream;

/** The canonical remaining timeline consumed by both the app and its widget. */
public record TodayTimeline(
        LocalDate day,
        LocalDateTime generatedAt,
        LocalDateTime nextRefreshAt,
        List<Entry> entries) {
    public TodayTimeline {
        if (day == null || generatedAt == null || nextRefreshAt == null) {
            throw new IllegalArgumentException("Heute-Zeitbezug fehlt");
        }
        entries = List.copyOf(entries);
    }

    public static TodayTimeline from(DashboardData dashboard, LocalDateTime now) {
        LocalDate day = now.toLocalDate();
        LocalDateTime dayStart = day.atStartOfDay();
        LocalDateTime dayEnd = dayStart.plusDays(1);
        List<Entry> entries = new ArrayList<>();
        dashboard.focus().stream()
                .filter(value -> value.start().toLocalDate().equals(day))
                .filter(value -> value.end().isAfter(now))
                .map(value -> new Assignment(value, preceding(value, dashboard.calendar())))
                .forEach(entries::add);
        dashboard.calendar().stream()
                .filter(value -> value.start().isBefore(dayEnd))
                .filter(value -> value.end().isAfter(dayStart))
                .filter(value -> value.end().isAfter(now))
                .map(Calendar::new)
                .forEach(entries::add);
        entries.sort(Comparator.comparing(Entry::start)
                .thenComparingInt(value -> value instanceof Calendar ? 0 : 1)
                .thenComparing(Entry::stableId));
        LinkedHashMap<String, Entry> unique = new LinkedHashMap<>();
        entries.forEach(value -> unique.putIfAbsent(value.stableId(), value));
        entries = new ArrayList<>(unique.values());
        LocalDateTime nextRefresh = Stream.concat(Stream.of(dayEnd), entries.stream()
                        .flatMap(value -> Stream.of(value.start(), value.end())))
                .filter(value -> value.isAfter(now))
                .min(LocalDateTime::compareTo).orElse(dayEnd);
        return new TodayTimeline(day, now, nextRefresh, entries);
    }

    private static BusyInterval preceding(
            PlanAssignment assignment, List<BusyInterval> calendar) {
        return calendar.stream()
                .filter(value -> !value.end().isAfter(assignment.start()))
                .filter(value -> value.end().toLocalDate().equals(
                        assignment.start().toLocalDate()))
                .max(Comparator.comparing(BusyInterval::end)
                        .thenComparing(BusyInterval::start)
                        .thenComparing(BusyInterval::id))
                .orElse(null);
    }

    public sealed interface Entry permits Assignment, Calendar {
        LocalDateTime start();
        LocalDateTime end();
        String title();
        String stableId();
    }

    public record Assignment(PlanAssignment value, BusyInterval precedingCalendar)
            implements Entry {
        @Override public LocalDateTime start() { return value.start(); }
        @Override public LocalDateTime end() { return value.end(); }
        @Override public String title() { return value.workItem().title(); }
        @Override public String stableId() {
            return "focus:" + value.workItem().id() + ":" + value.occurrenceKey();
        }
    }

    public record Calendar(BusyInterval value) implements Entry {
        @Override public LocalDateTime start() { return value.start(); }
        @Override public LocalDateTime end() { return value.end(); }
        @Override public String title() { return value.title(); }
        @Override public String stableId() { return "calendar:" + value.id(); }
    }
}
