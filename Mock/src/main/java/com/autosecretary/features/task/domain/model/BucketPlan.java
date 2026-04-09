package com.autosecretary.features.task.domain.model;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Aggregate output of planning for one day.
 *
 * <h2>Who consumes this</h2>
 * Plan persistence gateway, UI list projection, and completion-triggered replanning.
 *
 * <h2>Why include remaining minutes</h2>
 * Enables deterministic refill decisions and transparent diagnostics without recomputing
 * capacity from raw windows/calendar blocks in every caller.
 */
public record BucketPlan(LocalDate day,
                         Map<TimeSlot, List<BucketPlanEntry>> entriesBySlot,
                         Map<TimeSlot, Integer> remainingMinutesBySlot) {
}
