package com.autosecretary.features.task.domain;

import java.time.LocalTime;

/**
 * Domain-level representation of a calendar event used during task scheduling and UI flows.
 *
 * <p>This is a thin domain abstraction over the device's Android calendar entries. It is
 * produced by {@code CalendarReader} (from the raw {@code CalendarContract} cursor) and
 * consumed by the package-private {@code generateSlotsForDay} overload in
 * {@code DefaultTaskSlotGenerator} that accepts an explicit list of calendar events. Events
 * are used to populate the occupied-interval list so the scheduler avoids placing task slots
 * that overlap with existing calendar commitments.
 *
 * <p>Distinct from {@link com.autosecretary.features.task.domain.scheduling.CalendarBlockedIntervalProvider.BlockedInterval},
 * which is the runtime interface used during actual generation; this record is a simpler DTO
 * used when calendar data is passed explicitly (e.g., in tests or from the single-day path).
 *
 * @param title Human-readable title of the calendar event
 * @param start Interval start (inclusive)
 * @param end   Interval end (exclusive); follows the {@code [start, end)} half-open convention
 *              used by {@code BlockedInterval} and {@code SchedulingWindow}
 */
public record TaskCalendarEvent(String title, LocalTime start, LocalTime end) {
}
