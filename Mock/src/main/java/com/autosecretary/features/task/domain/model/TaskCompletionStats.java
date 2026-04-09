package com.autosecretary.features.task.domain.model;

import java.time.Instant;

/**
 * Scoring-facing completion metrics derived from full completion history.
 */
public record TaskCompletionStats(int currentStreakDays,
                                  int longestStreakDays,
                                  Instant lastCompletedAtUtc,
                                  int completionCount7d,
                                  int completionCount30d,
                                  long completionCountTotal) {
}
