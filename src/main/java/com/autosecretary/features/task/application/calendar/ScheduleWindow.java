package com.autosecretary.features.task.application.calendar;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Defines a time range on a specific day for task schedule conflict checking.
 *
 * <p>Used by {@link TaskCalendarService} to query which device calendar events overlap with a
 * preferred task execution window. This enables task scheduling to detect and avoid time conflicts
 * during slot generation and daily list display.
 *
 * <p><strong>Constraints:</strong>
 * <ul>
 *   <li>{@code startTime} must be &le; {@code endTime} (forms a valid, non-empty window)</li>
 *   <li>Both times are assumed to be on the date specified by {@code day}</li>
 *   <li>The window defines a wall-clock time range; midnight edge cases are up to the caller</li>
 * </ul>
 *
 * <p><strong>Example:</strong> To check calendar events between 09:00 and 17:00 on 2025-01-15:
 * <pre>
 *   ScheduleWindow window = new ScheduleWindow(
 *       LocalDate.of(2025, 1, 15),
 *       LocalTime.of(9, 0),    // startTime = 09:00
 *       LocalTime.of(17, 0)    // endTime = 17:00
 *   );
 *   List&lt;TaskCalendarEvent&gt; conflicts = taskCalendarService.getEventsForDay(window);
 * </pre>
 *
 * <p>See {@link com.autosecretary.features.task.application.internal.calendar.CalendarReader}
 * for the device calendar implementation.
 */
public record ScheduleWindow(LocalDate day, LocalTime startTime, LocalTime endTime) {
}
