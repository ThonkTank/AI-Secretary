package com.autosecretary.features.task.domain.scheduling;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * Domain contract for querying calendar-based scheduling conflicts during task slot generation.
 *
 * <p><strong>Purpose:</strong> Provides read-only access to blocked time intervals (e.g., existing calendar
 * events, user-held time) that must not overlap with generated task slots.
 *
 * <p><strong>When consulted:</strong> During daily or window-based slot generation in {@link TaskSlotGenerator},
 * before placing a slot in a candidate time interval. If the slot overlaps any blocked interval, the slot
 * is rejected with reason {@link SchedulingConflict.ReasonCode#CALENDAR_OVERLAP}.
 *
 * <p><strong>Boundaries:</strong> {@code BlockedInterval} uses half-open intervals: start (inclusive) to
 * end (exclusive), matching {@code java.time} conventions.
 *
 * @see SchedulingWindowProvider
 * @see SchedulingConflict
 * @see com.autosecretary.features.task.application.internal.calendar.DeviceCalendarBlockedIntervalProvider
 */
public interface CalendarBlockedIntervalProvider {

    /**
     * Reads blocked intervals that fall within a specified time window on a given day.
     *
     * @param day The calendar day to check
     * @param windowStart Earliest time to consider (inclusive)
     * @param windowEnd Latest time to consider (exclusive)
     * @return List of non-overlapping blocked intervals within the window, or empty if none exist
     */
    List<BlockedInterval> readBlockedIntervals(LocalDate day, LocalDateTime windowStart, LocalDateTime windowEnd);

    /**
     * Immutable representation of a contiguous blocked time interval.
     *
     * @param start Interval start (inclusive)
     * @param end Interval end (exclusive)
     */
    record BlockedInterval(LocalDateTime start, LocalDateTime end) {
    }

    /** Singleton provider that reports no blocked intervals. Used as a default when calendar integration is disabled. */
    CalendarBlockedIntervalProvider NONE = (day, windowStart, windowEnd) -> Collections.emptyList();
}
