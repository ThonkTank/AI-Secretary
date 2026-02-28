/**
 * Cross-feature shared enums and constants used throughout the application.
 * <p>
 * This package contains definitions that are accessed by multiple features
 * (task scheduling, budget tracking, widget configuration) and should not be
 * tied to any single feature's domain layer.
 * <p>
 * <strong>Shared contents:</strong>
 * <ul>
 *   <li>{@link com.autosecretary.shared.Priority} — Task priority levels (LOW, MEDIUM, HIGH, CRITICAL)
 *       used by the task scheduler for daily planning. Values have multiplicative scoring weights.
 *   <li>{@link com.autosecretary.shared.Period} — Scheduling periods (DAY, WEEK, MONTH) with fixed
 *       day counts, used for task repetition patterns.
 *   <li>{@link com.autosecretary.shared.WidgetConfiguration} — App-wide widget configuration constants,
 *       shared between task and budget widgets.
 * </ul>
 * <p>
 * <strong>When to add new items:</strong> Only add to this package if the constant or enum is
 * truly used across multiple features. Single-feature enums belong in that feature's domain layer.
 */
package com.autosecretary.shared;
