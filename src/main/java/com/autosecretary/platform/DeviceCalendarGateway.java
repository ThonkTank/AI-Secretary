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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        String[] projection = {CalendarContract.Instances.BEGIN, CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.TITLE,
                CalendarContract.Instances.AVAILABILITY, CalendarContract.Instances.STATUS,
                CalendarContract.Instances.SELF_ATTENDEE_STATUS,
                CalendarContract.Instances.VISIBLE};
        try (Cursor cursor = context.getContentResolver().query(
                builder.build(), projection, null, null, CalendarContract.Instances.BEGIN)) {
            return cursor == null ? List.of() : intervals(cursor, zone);
        }
    }

    static List<BusyInterval> intervals(Cursor cursor, ZoneId zone) {
        Map<InstanceKey, BusyInterval> unique = new LinkedHashMap<>();
        while (cursor.moveToNext()) {
            if (!shouldInclude(cursor)) continue;
            LocalDateTime start = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(cursor.getLong(0)), zone);
            LocalDateTime finish = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(cursor.getLong(1)), zone);
            if (!finish.isAfter(start)) continue;
            String title = cursor.isNull(3) || cursor.getString(3).isBlank()
                    ? "Kalendertermin" : cursor.getString(3);
            BusyInterval interval = new BusyInterval(start, finish, title);
            unique.putIfAbsent(new InstanceKey(start, finish, title), interval);
        }
        return new ArrayList<>(unique.values());
    }

    private static boolean shouldInclude(Cursor cursor) {
        if (!cursor.isNull(2) && cursor.getInt(2) != 0) return false;
        if (!cursor.isNull(4) && cursor.getInt(4)
                == CalendarContract.Events.AVAILABILITY_FREE) return false;
        if (!cursor.isNull(5) && cursor.getInt(5)
                == CalendarContract.Events.STATUS_CANCELED) return false;
        if (!cursor.isNull(6) && cursor.getInt(6)
                == CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED) return false;
        return cursor.isNull(7) || cursor.getInt(7) != 0;
    }

    private record InstanceKey(LocalDateTime start, LocalDateTime end, String title) { }
}
