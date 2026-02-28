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

    /**
     * Converts epoch milliseconds to a LocalTime in the given timezone.
     * Used to convert Android CalendarContract timestamps.
     */
    private static LocalTime millisToLocalTime(long millis, ZoneId zoneId) {
        return Instant.ofEpochMilli(millis).atZone(zoneId).toLocalTime();
    }

    /**
     * Returns true if two time ranges have any overlap.
     * Used to filter events that fall outside the schedule window.
     */
    private static boolean timeRangesOverlap(LocalTime start1, LocalTime end1, LocalTime start2, LocalTime end2) {
        return start1.isBefore(end2) && end1.isAfter(start2);
    }

    /**
     * Constrains a time to be within [minBound, maxBound].
     * Used to normalize event times to the requested schedule window bounds.
     * Example: if event runs 14:00-15:30 but window is 13:00-14:30, clamp to 14:00-14:30.
     */
    private static LocalTime clamp(LocalTime time, LocalTime minBound, LocalTime maxBound) {
        if (time.isBefore(minBound)) return minBound;
        if (time.isAfter(maxBound)) return maxBound;
        return time;
    }

    /**
     * Returns the title if non-null and non-blank, otherwise returns the fallback.
     * Ensures all returned events have a valid display name for the UI.
     */
    private static String titleOrDefault(String title, String fallback) {
        return (title == null || title.isBlank()) ? fallback : title;
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

            LocalTime eventStart = millisToLocalTime(beginMillis, zoneId);
            LocalTime eventEnd = millisToLocalTime(endMillis, zoneId);

            if (!timeRangesOverlap(eventStart, eventEnd, scheduleStart, scheduleEnd)) {
                return null;
            }

            eventStart = clamp(eventStart, scheduleStart, scheduleEnd);
            eventEnd = clamp(eventEnd, scheduleStart, scheduleEnd);

            String safeTitle = titleOrDefault(title, FALLBACK_TITLE);
            return new TaskCalendarEvent(safeTitle, eventStart, eventEnd);
        });
    }
}
