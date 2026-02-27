package com.autosecretary.features.task.application.internal.calendar;

import android.content.ContentUris;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;

import com.autosecretary.features.task.domain.TaskCalendarEvent;
import com.autosecretary.features.task.application.calendar.TaskCalendarService;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Android-backed implementation of {@link TaskCalendarService}.
 */
public class CalendarReader implements TaskCalendarService {
    private static final String FALLBACK_TITLE = "Termin";

    private final Context context;

    public CalendarReader(Context context) {
        this.context = context.getApplicationContext();
    }

    private LocalTime millisToLocalTime(long millis, ZoneId zoneId) {
        return Instant.ofEpochMilli(millis).atZone(zoneId).toLocalTime();
    }

    private boolean timeRangesOverlap(LocalTime start1, LocalTime end1, LocalTime start2, LocalTime end2) {
        return start1.isBefore(end2) && end1.isAfter(start2);
    }

    private static LocalTime clampStart(LocalTime time, LocalTime minBound) {
        return time.isBefore(minBound) ? minBound : time;
    }

    private static LocalTime clampEnd(LocalTime time, LocalTime maxBound) {
        return time.isAfter(maxBound) ? maxBound : time;
    }

    private static String titleOrDefault(String title, String fallback) {
        return (title == null || title.isBlank()) ? fallback : title;
    }

    @Override
    public List<TaskCalendarEvent> getEventsForDay(LocalDate day,
                                                   LocalTime scheduleStart,
                                                   LocalTime scheduleEnd) {
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
                    continue;
                }

                LocalTime eventStart = millisToLocalTime(beginMillis, zoneId);
                LocalTime eventEnd = millisToLocalTime(endMillis, zoneId);

                if (!timeRangesOverlap(eventStart, eventEnd, scheduleStart, scheduleEnd)) {
                    continue;
                }

                eventStart = clampStart(eventStart, scheduleStart);
                eventEnd = clampEnd(eventEnd, scheduleEnd);

                String safeTitle = titleOrDefault(title, FALLBACK_TITLE);
                events.add(new TaskCalendarEvent(safeTitle, eventStart, eventEnd));
            }
        }

        return events;
    }
}
