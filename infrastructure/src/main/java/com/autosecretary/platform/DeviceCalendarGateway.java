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
import com.autosecretary.application.CalendarAvailability;
import com.autosecretary.application.CalendarOccurrence;
import com.autosecretary.application.CalendarOccurrenceId;
import com.autosecretary.application.CalendarParticipation;
import com.autosecretary.application.CalendarReadResult;
import com.autosecretary.application.CalendarStatus;
import com.autosecretary.application.CalendarVisibility;
import com.autosecretary.application.TimeRange;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Read-only calendar adapter for the complete planner horizon. */
public final class DeviceCalendarGateway implements CalendarPort {
    private final Context context;

    public DeviceCalendarGateway(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public CalendarReadResult read(TimeRange range) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR)
                != PackageManager.PERMISSION_GRANTED) {
            return new CalendarReadResult.PermissionMissing();
        }
        long begin = range.startInclusive().toEpochMilli();
        long end = range.endExclusive().toEpochMilli();
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
            return new CalendarReadResult.Available(
                    cursor == null ? List.of() : occurrences(cursor));
        }
    }

    static List<CalendarOccurrence> occurrences(Cursor cursor) {
        Map<CalendarOccurrenceId, CalendarOccurrence> unique = new LinkedHashMap<>();
        while (cursor.moveToNext()) {
            Instant startInstant = Instant.ofEpochMilli(cursor.getLong(
                    cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)));
            Instant finishInstant = Instant.ofEpochMilli(cursor.getLong(
                    cursor.getColumnIndexOrThrow(CalendarContract.Instances.END)));
            if (!finishInstant.isAfter(startInstant)) continue;
            int titleColumn = cursor.getColumnIndexOrThrow(CalendarContract.Instances.TITLE);
            Optional<String> title = cursor.isNull(titleColumn) ? Optional.empty()
                    : Optional.ofNullable(cursor.getString(titleColumn))
                            .filter(value -> !value.isBlank());
            long eventId = cursor.getLong(
                    cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_ID));
            long calendarId = cursor.getLong(
                    cursor.getColumnIndexOrThrow(CalendarContract.Instances.CALENDAR_ID));
            CalendarOccurrenceId id = new CalendarOccurrenceId(
                    calendarId, eventId, startInstant);
            boolean providerVisible = intValue(cursor, CalendarContract.Instances.VISIBLE)
                    .map(value -> value != 0).orElse(true);
            CalendarVisibility visibility = !providerVisible ? CalendarVisibility.HIDDEN
                    : title.isEmpty() ? CalendarVisibility.TITLE_HIDDEN
                    : CalendarVisibility.VISIBLE;
            CalendarOccurrence occurrence = new CalendarOccurrence(id, startInstant,
                    finishInstant,
                    intValue(cursor, CalendarContract.Instances.ALL_DAY)
                            .map(value -> value != 0).orElse(false),
                    availability(intValue(cursor, CalendarContract.Instances.AVAILABILITY)),
                    status(intValue(cursor, CalendarContract.Instances.STATUS)),
                    participation(intValue(cursor,
                            CalendarContract.Instances.SELF_ATTENDEE_STATUS)),
                    visibility, title);
            unique.putIfAbsent(id, occurrence);
        }
        ArrayList<CalendarOccurrence> result = new ArrayList<>(unique.values());
        result.sort(Comparator.comparing(CalendarOccurrence::start)
                .thenComparing(CalendarOccurrence::end)
                .thenComparing(value -> value.id().stableValue()));
        return result;
    }

    private static Optional<Integer> intValue(Cursor cursor, String column) {
        int index = cursor.getColumnIndexOrThrow(column);
        return cursor.isNull(index) ? Optional.empty() : Optional.of(cursor.getInt(index));
    }

    private static CalendarAvailability availability(Optional<Integer> value) {
        if (value.isEmpty()) return CalendarAvailability.UNKNOWN;
        return switch (value.get()) {
            case CalendarContract.Events.AVAILABILITY_BUSY -> CalendarAvailability.BUSY;
            case CalendarContract.Events.AVAILABILITY_TENTATIVE -> CalendarAvailability.TENTATIVE;
            case CalendarContract.Events.AVAILABILITY_FREE -> CalendarAvailability.FREE;
            default -> CalendarAvailability.UNKNOWN;
        };
    }

    private static CalendarStatus status(Optional<Integer> value) {
        if (value.isEmpty()) return CalendarStatus.UNKNOWN;
        return switch (value.get()) {
            case CalendarContract.Events.STATUS_CONFIRMED -> CalendarStatus.CONFIRMED;
            case CalendarContract.Events.STATUS_TENTATIVE -> CalendarStatus.TENTATIVE;
            case CalendarContract.Events.STATUS_CANCELED -> CalendarStatus.CANCELED;
            default -> CalendarStatus.UNKNOWN;
        };
    }

    private static CalendarParticipation participation(Optional<Integer> value) {
        if (value.isEmpty()) return CalendarParticipation.UNKNOWN;
        return switch (value.get()) {
            case CalendarContract.Attendees.ATTENDEE_STATUS_ACCEPTED ->
                    CalendarParticipation.ACCEPTED;
            case CalendarContract.Attendees.ATTENDEE_STATUS_TENTATIVE ->
                    CalendarParticipation.TENTATIVE;
            case CalendarContract.Attendees.ATTENDEE_STATUS_NONE ->
                    CalendarParticipation.NONE;
            case CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED ->
                    CalendarParticipation.DECLINED;
            default -> CalendarParticipation.UNKNOWN;
        };
    }
}
