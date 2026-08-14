package com.autosecretary.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/** A one-off item. Routine-only state cannot be represented here. */
public record Task(
        String id,
        String title,
        int durationMinutes,
        LocalDateTime deadlineAt,
        TimePreference timePreference,
        boolean flexible,
        List<Step> steps,
        LocalDateTime createdAt,
        boolean completed,
        CompletionStats stats,
        long revision) implements WorkItem {

    public Task {
        id = WorkItems.requireId(id);
        title = WorkItems.requireTitle(title);
        durationMinutes = WorkItems.requireDuration(durationMinutes);
        steps = WorkItems.normalizedSteps(steps);
        createdAt = Objects.requireNonNull(createdAt, "Erstellzeit fehlt");
        stats = stats == null ? CompletionStats.empty() : stats;
        if (revision < 0) throw new IllegalArgumentException("Revision darf nicht negativ sein");
    }

    @Override public boolean isOpenOn(LocalDate day) { return !completed; }
    @Override public LocalDate occurrenceDate(LocalDate day) { return null; }
}
