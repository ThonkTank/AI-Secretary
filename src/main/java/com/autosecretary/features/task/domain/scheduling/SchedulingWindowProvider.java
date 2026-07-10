package com.autosecretary.features.task.domain.scheduling;

import com.autosecretary.features.task.domain.model.TaskPrefSlotFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Domain contract for determining the daily time window during which task slots can be generated.
 *
 * <p><strong>Purpose:</strong> Called during slot generation to obtain the start and end times
 * for the current day (e.g., "8am to 11pm"). Slots placed outside this window are rejected with
 * reason {@link SchedulingConflict.ReasonCode#OUTSIDE_WINDOW}.
 *
 * <p><strong>Relationship to CalendarBlockedIntervalProvider:</strong> These are orthogonal concepts.
 * {@code SchedulingWindowProvider} sets the outer boundary of allowable times; {@link CalendarBlockedIntervalProvider}
 * identifies interior intervals (events, holds) that must be avoided. Both must be satisfied for a slot
 * to be placed successfully.
 *
 * <p><strong>Implementation:</strong> Typically configured via user settings (start/end time in app settings)
 * and injected by the application layer. Default implementation returns fixed times for all days via
 * {@link #DEFAULT}.
 *
 * @see CalendarBlockedIntervalProvider
 * @see TaskSlotGenerator
 * @see SchedulingConflict
 */
public interface SchedulingWindowProvider {
    /**
     * Determines the allowable scheduling window for a given day.
     *
     * @param day The calendar day to get the window for
     * @return A {@code SchedulingWindow} with start (inclusive) and end (exclusive) times
     */
    SchedulingWindow forDay(LocalDate day);

    /**
     * Immutable representation of a daily scheduling time window.
     *
     * @param start Window start time (inclusive)
     * @param end Window end time (exclusive)
     */
    record SchedulingWindow(LocalDateTime start, LocalDateTime end) {
    }

    /**
     * Default scheduling window provider: returns fixed start/end times for all days.
     *
     * <p>Times are sourced from {@link TaskPrefSlotFactory}, which defines app-wide defaults
     * (06:00–21:00, i.e. 6am to 9pm, configurable via app settings).
     */
    SchedulingWindowProvider DEFAULT = day -> {
        LocalDateTime start = LocalDateTime.of(day, TaskPrefSlotFactory.DEFAULT_START_TIME);
        LocalDateTime end = LocalDateTime.of(day, TaskPrefSlotFactory.DEFAULT_END_TIME);
        return new SchedulingWindow(start, end);
    };
}
