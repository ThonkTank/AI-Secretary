package com.autosecretary.domain;

/** Immutable completion counters shared by tasks and routines. */
public record CompletionStats(int currentStreak, int bestStreak, int totalCompletions) {
    public CompletionStats {
        if (currentStreak < 0 || bestStreak < 0 || totalCompletions < 0) {
            throw new IllegalArgumentException("Abschlussstatistiken dürfen nicht negativ sein");
        }
        if (bestStreak < currentStreak) {
            bestStreak = currentStreak;
        }
        if (currentStreak > totalCompletions || bestStreak > totalCompletions) {
            throw new IllegalArgumentException(
                    "Streaks dürfen die Zahl der Abschlüsse nicht überschreiten");
        }
    }

    public static CompletionStats empty() {
        return new CompletionStats(0, 0, 0);
    }
}
