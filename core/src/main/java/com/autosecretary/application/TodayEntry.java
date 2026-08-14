package com.autosecretary.application;

import com.autosecretary.domain.PlanAssignment;

import java.time.Instant;
import java.util.Optional;

public sealed interface TodayEntry permits TodayEntry.Focus, TodayEntry.Calendar {
    Instant start();
    Instant end();
    String stableId();
    Optional<String> title();

    record Focus(
            PlanAssignment value,
            CalendarOccurrence precedingCalendar,
            Instant start,
            Instant end) implements TodayEntry {
        public Focus {
            if (value == null || start == null || end == null || !end.isAfter(start)) {
                throw new IllegalArgumentException("Ungültiger Fokus-Eintrag");
            }
        }

        @Override public String stableId() {
            return "focus:" + value.workItem().id() + ":" + value.occurrenceKey();
        }

        @Override public Optional<String> title() {
            return Optional.of(value.workItem().title());
        }
    }

    record Calendar(CalendarOccurrence value) implements TodayEntry {
        public Calendar {
            if (value == null) throw new IllegalArgumentException("Kalendertermin fehlt");
        }

        @Override public Instant start() { return value.start(); }
        @Override public Instant end() { return value.end(); }
        @Override public String stableId() { return "calendar:" + value.id().stableValue(); }
        @Override public Optional<String> title() { return value.title(); }
    }
}
