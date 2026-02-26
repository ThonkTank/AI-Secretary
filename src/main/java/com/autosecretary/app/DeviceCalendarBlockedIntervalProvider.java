package com.autosecretary.app;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.provider.CalendarContract;

import androidx.core.content.ContextCompat;

import com.autosecretary.features.task.domain.CalendarBlockedIntervalProvider;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

public class DeviceCalendarBlockedIntervalProvider implements CalendarBlockedIntervalProvider {
    private final Context context;

    public DeviceCalendarBlockedIntervalProvider(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public List<BlockedInterval> readBlockedIntervals(LocalDate day,
                                                      LocalDateTime windowStart,
                                                      LocalDateTime windowEnd) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR)
                != PackageManager.PERMISSION_GRANTED) {
            return new ArrayList<>();
        }

        ZoneId zone = ZoneId.systemDefault();
        long dayStartMillis = day.atStartOfDay(zone).toInstant().toEpochMilli();
        long dayEndMillis = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();

        String[] projection = {
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END
        };

        String selection = CalendarContract.Instances.BEGIN + " < ? AND "
                + CalendarContract.Instances.END + " > ?";
        String[] args = {
                String.valueOf(dayEndMillis),
                String.valueOf(dayStartMillis)
        };

        List<BlockedInterval> intervals = new ArrayList<>();
        try (Cursor cursor = context.getContentResolver().query(
                CalendarContract.Instances.CONTENT_URI.buildUpon()
                        .appendPath(String.valueOf(dayStartMillis))
                        .appendPath(String.valueOf(dayEndMillis))
                        .build(),
                projection,
                selection,
                args,
                CalendarContract.Instances.BEGIN + " ASC"
        )) {
            if (cursor == null) {
                return intervals;
            }
            int beginIndex = cursor.getColumnIndex(CalendarContract.Instances.BEGIN);
            int endIndex = cursor.getColumnIndex(CalendarContract.Instances.END);
            while (cursor.moveToNext()) {
                long begin = cursor.getLong(beginIndex);
                long end = cursor.getLong(endIndex);
                LocalDateTime eventStart = LocalDateTime.ofInstant(Instant.ofEpochMilli(begin), zone);
                LocalDateTime eventEnd = LocalDateTime.ofInstant(Instant.ofEpochMilli(end), zone);
                LocalDateTime clampedStart = eventStart.isBefore(windowStart) ? windowStart : eventStart;
                LocalDateTime clampedEnd = eventEnd.isAfter(windowEnd) ? windowEnd : eventEnd;
                if (clampedEnd.isAfter(clampedStart)) {
                    intervals.add(new BlockedInterval(clampedStart, clampedEnd));
                }
            }
        }
        return intervals;
    }
}
