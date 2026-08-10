package com.autosecretary.data;

import android.Manifest;
import android.content.ContentUris;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;

import androidx.core.content.ContextCompat;

import com.autosecretary.core.CalendarBlock;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Reads busy intervals. There is intentionally no calendar write API anywhere in the app. */
public final class DeviceCalendarReader {
    private final Context context;

    public DeviceCalendarReader(Context context) {
        this.context = context.getApplicationContext();
    }

    public List<CalendarBlock> read(LocalDate day) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR)
                != PackageManager.PERMISSION_GRANTED) {
            return Collections.emptyList();
        }
        ZoneId zone = ZoneId.systemDefault();
        long begin = day.atStartOfDay(zone).toInstant().toEpochMilli();
        long end = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
        Uri.Builder builder = CalendarContract.Instances.CONTENT_URI.buildUpon();
        ContentUris.appendId(builder, begin);
        ContentUris.appendId(builder, end);

        List<CalendarBlock> result = new ArrayList<>();
        String[] projection = {
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY
        };
        try (Cursor cursor = context.getContentResolver().query(
                builder.build(), projection, null, null, CalendarContract.Instances.BEGIN)) {
            if (cursor == null) return result;
            while (cursor.moveToNext()) {
                if (cursor.getInt(2) != 0) continue;
                LocalDateTime start = LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(cursor.getLong(0)), zone);
                LocalDateTime finish = LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(cursor.getLong(1)), zone);
                if (finish.isAfter(start)) result.add(new CalendarBlock(start, finish));
            }
        }
        return result;
    }
}
