package com.autosecretary.features.task.application.internal.calendar;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.provider.CalendarContract;

import androidx.core.content.ContextCompat;

import com.autosecretary.features.task.domain.scheduling.CalendarBlockedIntervalProvider;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Android device calendar implementation of {@link CalendarBlockedIntervalProvider}.
 *
 * <p>Reads calendar events from the system calendar and returns them as "blocked intervals" —
 * time periods that are unavailable for task scheduling. This is used by domain-level scheduling
 * algorithms (e.g., slot scoring and generation) to avoid scheduling tasks during busy times.
 *
 * <p>Unlike {@link CalendarReader} (which is application-facing and includes event titles for UI display),
 * this provider is domain-facing and returns minimal blocking-time data: just start and end times.
 * Domain code uses blocked intervals to adjust scoring, filter slot candidates, and validate
 * schedule feasibility.
 *
 * <p><strong>Permission:</strong> Requires {@code android.permission.READ_CALENDAR}.
 * If not granted, returns an empty list. The UI layer is responsible for requesting this permission.
 *
 * <p><strong>Time window semantics:</strong> Returned intervals are clamped to the requested window
 * bounds ([windowStart, windowEnd]). No interval in the result will extend outside this window.
 *
 * <p><strong>See also:</strong> {@link CalendarBlockedIntervalProvider} interface defines the contract.
 */
public class DeviceCalendarBlockedIntervalProvider implements CalendarBlockedIntervalProvider {
    private final Context context;

    public DeviceCalendarBlockedIntervalProvider(Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * Reads calendar events for the given day and returns the intervals that conflict
     * with the specified time window.
     *
     * @param day            The calendar day to query (events are fetched for this entire day)
     * @param windowStart    Start of the scheduling window (inclusive)
     * @param windowEnd      End of the scheduling window (exclusive)
     * @return A list of {@link BlockedInterval} objects representing times occupied by calendar events.
     *         Each interval is clamped to [windowStart, windowEnd]. If permission is denied,
     *         returns an empty list. If no events overlap the window, returns an empty list.
     */
    @Override
    public List<BlockedInterval> readBlockedIntervals(LocalDate day,
                                                      LocalDateTime windowStart,
                                                      LocalDateTime windowEnd) {
        // Check for READ_CALENDAR permission using ContextCompat (recommended for API compatibility).
        // If permission is missing, return empty list (no calendar data available).
        // The UI layer is responsible for requesting this permission when needed.
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR)
                != PackageManager.PERMISSION_GRANTED) {
            return new ArrayList<>();
        }

        ZoneId zone = ZoneId.systemDefault();
        // Query the entire day to get all calendar events, then filter to the scheduling window.
        long dayStartMillis = day.atStartOfDay(zone).toInstant().toEpochMilli();
        long dayEndMillis = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();

        String[] projection = {
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END
        };

        List<BlockedInterval> intervals = new ArrayList<>();
        try (Cursor cursor = context.getContentResolver().query(
                CalendarContract.Instances.CONTENT_URI.buildUpon()
                        .appendPath(String.valueOf(dayStartMillis))
                        .appendPath(String.valueOf(dayEndMillis))
                        .build(),
                projection,
                null,
                null,
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

                // Clamp event times to the requested window bounds.
                // If event starts before window, use window start; if it ends after, use window end.
                // This ensures returned intervals never exceed the window.
                LocalDateTime clampedStart = eventStart.isBefore(windowStart) ? windowStart : eventStart;
                LocalDateTime clampedEnd = eventEnd.isAfter(windowEnd) ? windowEnd : eventEnd;

                // Only add intervals that actually fall within the window (clampedEnd > clampedStart).
                // This filters out events that don't overlap the requested time window.
                if (clampedEnd.isAfter(clampedStart)) {
                    intervals.add(new BlockedInterval(clampedStart, clampedEnd));
                }
            }
        }
        return intervals;
    }
}
