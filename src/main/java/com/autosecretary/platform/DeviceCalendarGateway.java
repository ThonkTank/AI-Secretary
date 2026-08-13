package com.autosecretary.platform;

import android.Manifest;
import android.content.ContentUris;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;

import androidx.core.content.ContextCompat;

import com.autosecretary.application.CalendarPort;
import com.autosecretary.domain.BusyInterval;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/** Read-only calendar adapter for the complete planner horizon. */
public final class DeviceCalendarGateway implements CalendarPort {
    private final Context context;

    public DeviceCalendarGateway(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public List<BusyInterval> read(LocalDate fromInclusive, LocalDate toExclusive) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR)
                != PackageManager.PERMISSION_GRANTED) return List.of();
        ZoneId zone = ZoneId.systemDefault();
        long begin = fromInclusive.atStartOfDay(zone).toInstant().toEpochMilli();
        long end = toExclusive.atStartOfDay(zone).toInstant().toEpochMilli();
        Uri.Builder builder = CalendarContract.Instances.CONTENT_URI.buildUpon();
        ContentUris.appendId(builder, begin);
        ContentUris.appendId(builder, end);
        List<BusyInterval> result = new ArrayList<>();
        String[] projection = {CalendarContract.Instances.BEGIN, CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.TITLE};
        try (Cursor cursor = context.getContentResolver().query(
                builder.build(), projection, null, null, CalendarContract.Instances.BEGIN)) {
            if (cursor == null) return result;
            while (cursor.moveToNext()) {
                if (cursor.getInt(2) != 0) continue;
                LocalDateTime start = LocalDateTime.ofInstant(Instant.ofEpochMilli(cursor.getLong(0)), zone);
                LocalDateTime finish = LocalDateTime.ofInstant(Instant.ofEpochMilli(cursor.getLong(1)), zone);
                if (finish.isAfter(start)) result.add(new BusyInterval(start, finish,
                        cursor.isNull(3) ? "Termin" : cursor.getString(3)));
            }
        }
        return result;
    }
}
