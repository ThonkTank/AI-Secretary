package com.autosecretary.features.task.domain.model;

import java.time.LocalTime;

/**
 * Effective time range for one bucket on one day.
 *
 * <h2>Who produces/consumes</h2>
 * Produced by config gateway, consumed by capacity computation.
 *
 * <h2>Why include slot in record</h2>
 * Self-contained object can be logged/validated without external lookup.
 */
public record BucketWindow(TimeSlot slot, LocalTime startInclusive, LocalTime endExclusive) {
}
