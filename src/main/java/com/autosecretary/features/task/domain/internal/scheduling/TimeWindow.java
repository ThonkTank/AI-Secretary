package com.autosecretary.features.task.domain.internal.scheduling;

import java.time.LocalDateTime;

/**
 * Immutable boundaries of a daily scheduling window.
 */
record TimeWindow(LocalDateTime start, LocalDateTime end) {
}
