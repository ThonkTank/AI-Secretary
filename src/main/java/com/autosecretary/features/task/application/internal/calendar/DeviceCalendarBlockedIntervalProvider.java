package com.autosecretary.features.task.application.internal.calendar;

import android.database.Cursor;
import android.provider.CalendarContract;

import com.autosecretary.features.task.domain.scheduling.CalendarBlockedIntervalProvider;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
 * <p><strong>All-day events:</strong> Calendar all-day events are excluded from results because
 * they do not represent specific time blocks — task scheduling cares only about timed conflicts.
 *
 * <p><strong>Note:</strong> Calendar query boilerplate is shared with {@link CalendarReader}
 * via {@link CalendarQueryHelper}. Changes to permission checking, URI building, or query
 * execution must be coordinated in that helper.
 *
 * <p><strong>See also:</strong> {@link CalendarBlockedIntervalProvider} interface defines the contract.
 */
public class DeviceCalendarBlockedIntervalProvider implements CalendarBlockedIntervalProvider {
    private final CalendarQueryHelper queryHelper;

    public DeviceCalendarBlockedIntervalProvider(android.content.Context context) {
        this.queryHelper = new CalendarQueryHelper(context);
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
        ZoneId zone = ZoneId.systemDefault();

        String[] projection = {
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY
        };

        return queryHelper.queryDay(day, projection, cursor -> {
            boolean allDay = cursor.getInt(cursor.getColumnIndexOrThrow(CalendarContract.Instances.ALL_DAY)) != 0;
            if (allDay) {
                // All-day events do not represent specific time blocks.
                // Task scheduling only cares about timed conflicts, so skip them.
                return null;
            }

            long begin = cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN));
            long end = cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Instances.END));
            LocalDateTime eventStart = LocalDateTime.ofInstant(Instant.ofEpochMilli(begin), zone);
            LocalDateTime eventEnd = LocalDateTime.ofInstant(Instant.ofEpochMilli(end), zone);

            // Clamp event times to the requested window bounds.
            LocalDateTime clampedStart = eventStart.isBefore(windowStart) ? windowStart : eventStart;
            LocalDateTime clampedEnd = eventEnd.isAfter(windowEnd) ? windowEnd : eventEnd;

            // Only add intervals that actually fall within the window.
            if (clampedEnd.isAfter(clampedStart)) {
                return new BlockedInterval(clampedStart, clampedEnd);
            }
            return null;
        });
    }
}
