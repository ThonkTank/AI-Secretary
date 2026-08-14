package com.autosecretary.application;

import com.autosecretary.domain.BusyInterval;
import com.autosecretary.domain.PlanAssignment;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Remaining tasks and fixed calendar commitments for one shared Today surface. */
public record TodayTimeline(List<Entry> entries) {
    public TodayTimeline {
        entries = List.copyOf(entries);
    }

    public static TodayTimeline from(DashboardData dashboard, LocalDateTime now) {
        LocalDateTime dayStart = now.toLocalDate().atStartOfDay();
        LocalDateTime dayEnd = dayStart.plusDays(1);
        List<Entry> entries = new ArrayList<>();
        dashboard.focus().stream()
                .filter(value -> value.start().toLocalDate().equals(now.toLocalDate()))
                .filter(value -> value.end().isAfter(now))
                .map(Assignment::new)
                .forEach(entries::add);
        dashboard.calendar().stream()
                .filter(value -> value.start().isBefore(dayEnd))
                .filter(value -> value.end().isAfter(dayStart))
                .filter(value -> value.end().isAfter(now))
                .map(Calendar::new)
                .forEach(entries::add);
        entries.sort(Comparator.comparing(Entry::start)
                .thenComparingInt(value -> value instanceof Calendar ? 0 : 1)
                .thenComparing(Entry::title));
        return new TodayTimeline(entries);
    }

    public sealed interface Entry permits Assignment, Calendar {
        LocalDateTime start();
        LocalDateTime end();
        String title();
    }

    public record Assignment(PlanAssignment value) implements Entry {
        @Override public LocalDateTime start() { return value.start(); }
        @Override public LocalDateTime end() { return value.end(); }
        @Override public String title() { return value.workItem().title(); }
    }

    public record Calendar(BusyInterval value) implements Entry {
        @Override public LocalDateTime start() { return value.start(); }
        @Override public LocalDateTime end() { return value.end(); }
        @Override public String title() { return value.title(); }
    }
}
