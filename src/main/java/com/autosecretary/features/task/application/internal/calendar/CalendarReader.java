package com.autosecretary.features.task.application.internal.calendar;

import android.database.Cursor;
import android.provider.CalendarContract;

import com.autosecretary.features.task.domain.TaskCalendarEvent;
import com.autosecretary.features.task.application.calendar.TaskCalendarService;
import com.autosecretary.features.task.application.calendar.ScheduleWindow;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Android-backed implementation of {@link TaskCalendarService}.
 *
 * <p>Reads calendar events from the device calendar that overlap with a given scheduling window,
 * allowing task scheduling to avoid time conflicts. This service is used during task list display
 * and slot generation to understand which times are already busy.
 *
 * <p><strong>Permission:</strong> Requires {@code android.permission.READ_CALENDAR}.
 * If not granted, returns an empty list (no events). The UI layer is responsible for requesting
 * this permission before task scheduling features can show calendar conflicts.
 *
 * <p><strong>All-day events:</strong> Calendar all-day events are excluded from results because
 * they do not represent specific time blocks — task scheduling cares only about timed conflicts.
 *
 * <p><strong>Time normalization:</strong> Returned events are clamped to the requested schedule
 * window bounds, so no event in the result will extend before or after the window start/end times.
 *
 * <p><strong>Note:</strong> Calendar query boilerplate is shared with
 * {@link DeviceCalendarBlockedIntervalProvider} via {@link CalendarQueryHelper}. Changes to
 * permission checking, URI building, or query execution must be coordinated in that helper.
 */
public class CalendarReader implements TaskCalendarService {
    // User-facing fallback title for events with no title (consistent with app's German UI language).
    // Used in task conflict display when a calendar event lacks a name.
    private static final String FALLBACK_TITLE = "Termin";

    private final CalendarQueryHelper queryHelper;

    public CalendarReader(android.content.Context context) {
        this.queryHelper = new CalendarQueryHelper(context);
    }

    @Override
    public List<TaskCalendarEvent> getEventsForDay(ScheduleWindow window) {
        LocalDate day = window.day();
        LocalTime scheduleStart = window.startTime();
        LocalTime scheduleEnd = window.endTime();
        ZoneId zoneId = ZoneId.systemDefault();

        String[] projection = {
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY
        };

        return queryHelper.queryDay(day, projection, cursor -> {
            String title = cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Instances.TITLE));
            long beginMillis = cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN));
            long endMillis = cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Instances.END));
            boolean allDay = cursor.getInt(cursor.getColumnIndexOrThrow(CalendarContract.Instances.ALL_DAY)) != 0;

            if (allDay) {
                // All-day events (e.g., birthdays, holidays) do not occupy a specific time slot.
                // Task scheduling only cares about timed conflicts, so skip them.
                return null;
            }

            LocalTime eventStart = Instant.ofEpochMilli(beginMillis).atZone(zoneId).toLocalTime();
            LocalTime eventEnd = Instant.ofEpochMilli(endMillis).atZone(zoneId).toLocalTime();

            // Skip if event doesn't overlap the schedule window
            if (eventStart.isAfter(scheduleEnd) || eventEnd.isBefore(scheduleStart)) {
                return null;
            }

            // Clamp event times to window bounds
            eventStart = CalendarQueryHelper.clamp(eventStart, scheduleStart, scheduleEnd);
            eventEnd = CalendarQueryHelper.clamp(eventEnd, scheduleStart, scheduleEnd);

            String safeTitle = (title == null || title.isBlank()) ? FALLBACK_TITLE : title;
            return new TaskCalendarEvent(safeTitle, eventStart, eventEnd);
        });
    }
}
