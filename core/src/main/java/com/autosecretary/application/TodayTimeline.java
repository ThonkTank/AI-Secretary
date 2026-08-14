package com.autosecretary.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Canonical semantic agenda shared by the app and widget. */
public record TodayTimeline(
        LocalDate day,
        Instant generatedAt,
        Instant nextRefreshAt,
        List<TodayEntry> entries,
        boolean undoAvailable) {
    public TodayTimeline {
        if (day == null || generatedAt == null || nextRefreshAt == null) {
            throw new IllegalArgumentException("Heute-Zeitbezug fehlt");
        }
        entries = List.copyOf(entries);
    }
}
