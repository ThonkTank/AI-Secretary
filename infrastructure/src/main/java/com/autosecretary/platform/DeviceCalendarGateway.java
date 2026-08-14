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
import com.autosecretary.application.TimeProvider;
import com.autosecretary.domain.BusyInterval;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Read-only calendar adapter for the complete planner horizon. */
public final class DeviceCalendarGateway implements CalendarPort {
    private final Context context;
    private final TimeProvider time;

    public DeviceCalendarGateway(Context context, TimeProvider time) {
        this.context = context.getApplicationContext();
        this.time = time;
    }

    @Override
    public List<BusyInterval> read(LocalDate fromInclusive, LocalDate toExclusive) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR)
                != PackageManager.PERMISSION_GRANTED) return List.of();
        ZoneId zone = time.zone();
        long begin = fromInclusive.atStartOfDay(zone).toInstant().toEpochMilli();
        long end = toExclusive.atStartOfDay(zone).toInstant().toEpochMilli();
        Uri.Builder builder = CalendarContract.Instances.CONTENT_URI.buildUpon();
        ContentUris.appendId(builder, begin);
        ContentUris.appendId(builder, end);
        String[] projection = {CalendarContract.Instances.BEGIN, CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.TITLE,
                CalendarContract.Instances.AVAILABILITY, CalendarContract.Instances.STATUS,
                CalendarContract.Instances.SELF_ATTENDEE_STATUS,
                CalendarContract.Instances.VISIBLE,
                CalendarContract.Instances.EVENT_ID,
                CalendarContract.Instances.CALENDAR_ID};
        try (Cursor cursor = context.getContentResolver().query(
                builder.build(), projection, null, null, CalendarContract.Instances.BEGIN)) {
            return cursor == null ? List.of() : intervals(cursor, zone);
        }
    }

    static List<BusyInterval> intervals(Cursor cursor, ZoneId zone) {
        Map<String, BusyInterval> unique = new LinkedHashMap<>();
        while (cursor.moveToNext()) {
            if (!shouldInclude(cursor)) continue;
            Instant startInstant = Instant.ofEpochMilli(cursor.getLong(
                    cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)));
            Instant finishInstant = Instant.ofEpochMilli(cursor.getLong(
                    cursor.getColumnIndexOrThrow(CalendarContract.Instances.END)));
            if (!finishInstant.isAfter(startInstant)) continue;
            LocalDateTime start = LocalDateTime.ofInstant(startInstant, zone);
            LocalDateTime finish = LocalDateTime.ofInstant(finishInstant, zone);
            // LocalDateTime cannot represent the repeated offset during the autumn DST overlap.
            // Preserve the real positive duration so the planner never drops that occurrence.
            if (!finish.isAfter(start)) {
                finish = start.plus(Duration.between(startInstant, finishInstant));
            }
            int titleColumn = cursor.getColumnIndexOrThrow(CalendarContract.Instances.TITLE);
            boolean hidden = cursor.isNull(titleColumn) || cursor.getString(titleColumn).isBlank();
            String title = hidden ? "Kalendertermin" : cursor.getString(titleColumn);
            long eventId = cursor.getLong(
                    cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_ID));
            long calendarId = cursor.getLong(
                    cursor.getColumnIndexOrThrow(CalendarContract.Instances.CALENDAR_ID));
            String id = calendarId + ":" + eventId + ":" + startInstant;
            BusyInterval interval = new BusyInterval(id, start, finish, title,
                    hidden ? BusyInterval.TitleVisibility.HIDDEN
                            : BusyInterval.TitleVisibility.VISIBLE);
            unique.putIfAbsent(id, interval);
        }
        ArrayList<BusyInterval> result = new ArrayList<>(unique.values());
        result.sort(Comparator.comparing(BusyInterval::start)
                .thenComparing(BusyInterval::end).thenComparing(BusyInterval::id));
        return result;
    }

    private static boolean shouldInclude(Cursor cursor) {
        int allDay = cursor.getColumnIndexOrThrow(CalendarContract.Instances.ALL_DAY);
        int availability = cursor.getColumnIndexOrThrow(CalendarContract.Instances.AVAILABILITY);
        int status = cursor.getColumnIndexOrThrow(CalendarContract.Instances.STATUS);
        int attendee = cursor.getColumnIndexOrThrow(
                CalendarContract.Instances.SELF_ATTENDEE_STATUS);
        int visible = cursor.getColumnIndexOrThrow(CalendarContract.Instances.VISIBLE);
        if (!cursor.isNull(allDay) && cursor.getInt(allDay) != 0) return false;
        if (!cursor.isNull(availability) && cursor.getInt(availability)
                == CalendarContract.Events.AVAILABILITY_FREE) return false;
        if (!cursor.isNull(status) && cursor.getInt(status)
                == CalendarContract.Events.STATUS_CANCELED) return false;
        if (!cursor.isNull(attendee) && cursor.getInt(attendee)
                == CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED) return false;
        return cursor.isNull(visible) || cursor.getInt(visible) != 0;
    }
}
