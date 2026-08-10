package com.autosecretary.core;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Anchored recurrence with one open occurrence and no duplicated backlog. */
public final class RoutineCadence {
    private RoutineCadence() {
    }

    public static void complete(Obligation obligation, LocalDate completionDay) {
        if (!obligation.isRoutine()) {
            obligation.completed = true;
            obligation.totalCompletions++;
            return;
        }

        int cadence = Math.max(1, obligation.cadenceDays);
        LocalDate due = obligation.nextDueDate != null
                ? obligation.nextDueDate
                : completionDay;
        long daysLate = Math.max(0, ChronoUnit.DAYS.between(due, completionDay));
        obligation.currentStreak = daysLate < cadence
                ? obligation.currentStreak + 1
                : 1;
        obligation.bestStreak = Math.max(obligation.bestStreak, obligation.currentStreak);
        obligation.totalCompletions++;

        LocalDate next = due;
        do {
            next = next.plusDays(cadence);
        } while (!next.isAfter(completionDay));
        obligation.nextDueDate = next;
    }
}
