package com.autosecretary.features.task.application.internal.calendar;

import android.Manifest;
import android.content.ContentUris;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;

import androidx.core.content.ContextCompat;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Encapsulates shared Android Calendar query logic for both application and domain calendar clients.
 *
 * <p>Both {@link CalendarReader} and {@link DeviceCalendarBlockedIntervalProvider} need to:
 * <ol>
 *   <li>Check READ_CALENDAR permission</li>
 *   <li>Compute day start/end in epoch milliseconds</li>
 *   <li>Build a URI with time bounds for the Instances API</li>
 *   <li>Execute a query and handle the cursor</li>
 * </ol>
 *
 * <p>This helper consolidates that boilerplate and lets each client provide a row processor
 * to map cursor rows to its own domain types (e.g., TaskCalendarEvent vs. BlockedInterval).
 *
 * <p><strong>Permissions:</strong> Requires {@code android.permission.READ_CALENDAR}.
 * Returns empty result if permission is denied.
 *
 * <p><strong>Design note:</strong> Extracting this helper prevents permission/URI/query logic
 * from diverging when the Android Calendar API evolves. It also reduces code that must be kept
 * in sync between two otherwise independent classes.
 */
class CalendarQueryHelper {
    private final Context context;

    CalendarQueryHelper(Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * Queries calendar instances for a single day and processes each row via a callback.
     *
     * <p>This method handles:
     * <ul>
     *   <li>Permission checking (returns empty if READ_CALENDAR is denied)</li>
     *   <li>Day boundary computation in the device timezone</li>
     *   <li>URI building and cursor management (try-with-resources)</li>
     * </ul>
     *
     * <p>The caller provides a row processor that maps cursor rows (positioned at each event)
     * to domain-specific results. The processor may also filter rows by returning null.
     *
     * @param day          The calendar day to query
     * @param projection   Column names to fetch from CalendarContract.Instances
     * @param processor    Called once per cursor row; may return null to skip a row
     * @param <T>          Type of result produced by the processor
     * @return List of processed results, or empty if permission is denied or processor returns all nulls
     */
    <T> List<T> queryDay(LocalDate day, String[] projection, CursorRowProcessor<T> processor) {
        // Check for READ_CALENDAR permission. Without it, we cannot access the system calendar.
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR)
                != PackageManager.PERMISSION_GRANTED) {
            return new ArrayList<>();
        }

        ZoneId zoneId = ZoneId.systemDefault();
        long dayStartMillis = day.atStartOfDay(zoneId).toInstant().toEpochMilli();
        long dayEndMillis = day.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli();

        Uri.Builder builder = CalendarContract.Instances.CONTENT_URI.buildUpon();
        ContentUris.appendId(builder, dayStartMillis);
        ContentUris.appendId(builder, dayEndMillis);

        List<T> results = new ArrayList<>();
        try (Cursor cursor = context.getContentResolver().query(
                builder.build(),
                projection,
                null,
                null,
                CalendarContract.Instances.BEGIN + " ASC"
        )) {
            if (cursor == null) {
                return results;
            }

            while (cursor.moveToNext()) {
                T result = processor.process(cursor);
                if (result != null) {
                    results.add(result);
                }
            }
        }

        return results;
    }

    /**
     * Callback for processing a single cursor row into a domain-specific result.
     *
     * <p>The cursor is already positioned at the row. The processor may examine columns
     * and return a domain object, or return null to skip the row.
     */
    @FunctionalInterface
    interface CursorRowProcessor<T> {
        /**
         * Processes a cursor row (at the current position).
         *
         * @param cursor The cursor at the current row
         * @return A domain-specific result, or null to skip this row
         */
        T process(Cursor cursor);
    }
}
