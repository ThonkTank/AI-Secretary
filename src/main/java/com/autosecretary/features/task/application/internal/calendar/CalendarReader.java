package com.autosecretary.features.task.application.internal.calendar;

import android.content.ContentUris;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;

import com.autosecretary.features.task.domain.TaskCalendarEvent;
import com.autosecretary.features.task.application.calendar.TaskCalendarService;
import com.autosecretary.features.task.application.calendar.ScheduleWindow;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
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
 */
public class CalendarReader implements TaskCalendarService {
    // User-facing fallback title for events with no title (consistent with app's German UI language).
    // Used in task conflict display when a calendar event lacks a name.
    private static final String FALLBACK_TITLE = "Termin";

    private final Context context;

    public CalendarReader(Context context) {
        this.context = context.getApplicationContext();
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

        // Check for READ_CALENDAR permission. Without it, we cannot access the system calendar.
        // If permission is missing, return empty list (no calendar conflicts known).
        // The UI layer is responsible for requesting this permission when needed.
        if (context.checkSelfPermission(android.Manifest.permission.READ_CALENDAR)
                != PackageManager.PERMISSION_GRANTED) {
            return new ArrayList<>();
        }

        ZoneId zoneId = ZoneId.systemDefault();
        long dayStartMillis = day.atStartOfDay(zoneId).toInstant().toEpochMilli();
        long dayEndMillis = day.atTime(LocalTime.MAX).atZone(zoneId).toInstant().toEpochMilli();

        Uri.Builder builder = CalendarContract.Instances.CONTENT_URI.buildUpon();
        ContentUris.appendId(builder, dayStartMillis);
        ContentUris.appendId(builder, dayEndMillis);

        String[] projection = {
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY
        };

        List<TaskCalendarEvent> events = new ArrayList<>();
        try (Cursor cursor = context.getContentResolver().query(
                builder.build(),
                projection,
                null,
                null,
                CalendarContract.Instances.BEGIN + " ASC"
        )) {
            if (cursor == null) {
                return events;
            }

            int titleCol  = cursor.getColumnIndexOrThrow(CalendarContract.Instances.TITLE);
            int beginCol  = cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN);
            int endCol    = cursor.getColumnIndexOrThrow(CalendarContract.Instances.END);
            int allDayCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.ALL_DAY);

            while (cursor.moveToNext()) {
                String title = cursor.getString(titleCol);
                long beginMillis = cursor.getLong(beginCol);
                long endMillis = cursor.getLong(endCol);
                boolean allDay = cursor.getInt(allDayCol) != 0;

                if (allDay) {
                    // All-day events (e.g., birthdays, holidays) do not occupy a specific time slot.
                    // Task scheduling only cares about timed conflicts, so skip them.
                    // Note: If all-day events should be treated as "full-day blocking" in the future,
                    // this logic would need to change and return expanded blocked time.
                    continue;
                }

                LocalTime eventStart = millisToLocalTime(beginMillis, zoneId);
                LocalTime eventEnd = millisToLocalTime(endMillis, zoneId);

                if (!timeRangesOverlap(eventStart, eventEnd, scheduleStart, scheduleEnd)) {
                    continue;
                }

                eventStart = clamp(eventStart, scheduleStart, scheduleEnd);
                eventEnd = clamp(eventEnd, scheduleStart, scheduleEnd);

                String safeTitle = titleOrDefault(title, FALLBACK_TITLE);
                events.add(new TaskCalendarEvent(safeTitle, eventStart, eventEnd));
            }
        }

        return events;
    }
}
