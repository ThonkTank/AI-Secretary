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
        Cursor cursor = context.getContentResolver().query(
                builder.build(),
                projection,
                null,
                null,
                CalendarContract.Instances.BEGIN + " ASC"
        );

        if (cursor == null) {
            return events;
        }

        try {
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

                LocalTime eventStart = Instant.ofEpochMilli(beginMillis)
                        .atZone(zoneId).toLocalTime();
                LocalTime eventEnd = Instant.ofEpochMilli(endMillis)
                        .atZone(zoneId).toLocalTime();

                if (!eventStart.isBefore(scheduleEnd) || !eventEnd.isAfter(scheduleStart)) {
                    continue;
                }

                if (eventStart.isBefore(scheduleStart)) {
                    eventStart = scheduleStart;
                }
                if (eventEnd.isAfter(scheduleEnd)) {
                    eventEnd = scheduleEnd;
                }

                String safeTitle = (title == null || title.isBlank()) ? FALLBACK_TITLE : title;
                events.add(new TaskCalendarEvent(safeTitle, eventStart, eventEnd));
            }
        } finally {
            cursor.close();
        }

        return events;
    }
}
