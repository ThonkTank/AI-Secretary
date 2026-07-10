package com.autosecretary.features.task.domain;

import java.time.LocalTime;

/**
 * Application-facing representation of a calendar event used for UI flows.
 *
 * <p>This is a thin domain abstraction over the device's Android calendar entries. It is
 * produced by {@code CalendarReader} (from the raw {@code CalendarContract} cursor) and
 * consumed by UI-facing code that needs both a display title and local-time bounds.
 *
 * <p>Scheduling internals use
 * {@link com.autosecretary.features.task.domain.scheduling.CalendarBlockedIntervalProvider.BlockedInterval}
 * instead, because they only need start/end timestamps and not the title.
 *
 * @param title Human-readable title of the calendar event
 * @param start Interval start (inclusive)
 * @param end   Interval end (exclusive); follows the {@code [start, end)} half-open convention
 *              used by {@code BlockedInterval} and {@code SchedulingWindow}
 */
public record TaskCalendarEvent(String title, LocalTime start, LocalTime end) {
}
