package com.autosecretary.features.task.application.calendar;

import com.autosecretary.features.task.domain.TaskCalendarEvent;

import java.util.List;

/**
 * Reads device calendar events that overlap with a specified time window.
 *
 * <p>This service queries the device calendar to identify conflicting time blocks during task
 * scheduling. It is primarily used by task slot generation and the daily task list to surface
 * calendar conflicts to the user.
 *
 * <p><strong>Contract:</strong>
 * <ul>
 *   <li><strong>Permission:</strong> Requires {@code android.permission.READ_CALENDAR}.
 *       If permission is not granted, returns an empty list (no conflicts known).</li>
 *   <li><strong>All-day events:</strong> Excluded from results because they do not represent
 *       timed conflicts. All-day events (birthdays, holidays) occupy no specific time slot.</li>
 *   <li><strong>Time normalization:</strong> Returned events are clamped to the requested
 *       window bounds. If an event extends beyond the window, its start/end times are adjusted
 *       to fit within the window.</li>
 *   <li><strong>Return order:</strong> Events are sorted by start time (ascending).</li>
 *   <li><strong>Time zone:</strong> Events are converted to local (system default) time.</li>
 * </ul>
 *
 * <p><strong>Example:</strong>
 * <pre>
 *   ScheduleWindow window = new ScheduleWindow(
 *       LocalDate.now(),
 *       LocalTime.of(9, 0),
 *       LocalTime.of(17, 0)
 *   );
 *   List&lt;TaskCalendarEvent&gt; conflicts = taskCalendarService.getEventsForDay(window);
 *   // Returns all device calendar events in the 09:00–17:00 range on today's date,
 *   // clamped to those bounds, excluding all-day events.
 * </pre>
 *
 * <p>See {@link com.autosecretary.features.task.application.internal.calendar.CalendarReader}
 * for the default Android implementation.
 *
 * @see android.provider.CalendarContract.Instances
 * @see android.Manifest.permission.READ_CALENDAR
 */
public interface TaskCalendarService {
    /**
     * Returns calendar events overlapping the specified time window.
     *
     * @param window the day and time range to query (see {@link ScheduleWindow} contract)
     * @return a list of calendar events overlapping the window, or empty if permission is denied
     */
    List<TaskCalendarEvent> getEventsForDay(ScheduleWindow window);
}
