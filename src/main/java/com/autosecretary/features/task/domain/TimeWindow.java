package com.autosecretary.features.task.domain;

import java.time.LocalDateTime;

/**
 * Immutable boundaries of a scheduling window. Supplied fresh to each
 * {@link SlotGenerator#generateSlots} invocation to avoid stale-time issues.
 */
public record TimeWindow(LocalDateTime start, LocalDateTime end) {
}
