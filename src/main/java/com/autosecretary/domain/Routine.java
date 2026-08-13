package com.autosecretary.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/** A recurring item with exactly one open occurrence and a day cadence. */
public record Routine(
        String id,
        String title,
        int durationMinutes,
        LocalDateTime deadlineAt,
        TimePreference timePreference,
        boolean flexible,
        List<Step> steps,
        LocalDateTime createdAt,
        int cadenceDays,
        LocalDate nextDueDate,
        CompletionStats stats,
        long revision) implements WorkItem {

    public Routine {
        id = WorkItems.requireId(id);
        title = WorkItems.requireTitle(title);
        durationMinutes = WorkItems.requireDuration(durationMinutes);
        if (deadlineAt != null) {
            throw new IllegalArgumentException("Routinen verwenden eine Fälligkeit statt einer Deadline");
        }
        steps = WorkItems.normalizedSteps(steps);
        createdAt = Objects.requireNonNull(createdAt, "Erstellzeit fehlt");
        if (cadenceDays < 1) throw new IllegalArgumentException("Routine-Kadenz muss positiv sein");
        if (nextDueDate == null) throw new IllegalArgumentException("Routine-Fälligkeit fehlt");
        stats = stats == null ? CompletionStats.empty() : stats;
        if (revision < 0) throw new IllegalArgumentException("Revision darf nicht negativ sein");
    }

    @Override public boolean isOpenOn(LocalDate day) { return !nextDueDate.isAfter(day); }
    @Override public LocalDate occurrenceDate(LocalDate day) { return nextDueDate; }
}
