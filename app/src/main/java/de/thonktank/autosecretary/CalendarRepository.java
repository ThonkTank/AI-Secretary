package de.thonktank.autosecretary;

import android.Manifest;
import android.content.ContentUris;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import de.thonktank.autosecretary.calendar.CalendarDataSource;
import de.thonktank.autosecretary.infrastructure.AppLogger;

/** Read-only projection of today's visible Google Calendar instances. */
public final class CalendarRepository implements CalendarDataSource {
    private static final String TAG = "CalendarRepository";
    private final Context context;
    private final Clock clock;
    private final ZoneIdProvider zones;
    private final AppLogger logger;

    public CalendarRepository(Context context, Clock clock, ZoneIdProvider zones, AppLogger logger) {
        this.context = context.getApplicationContext();
        this.clock = clock;
        this.zones = zones;
        this.logger = logger;
    }

    @Override public List<CalendarEventSnapshot> today() {
        if (context.checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED)
            return Collections.emptyList();
        ZoneId zone = zones.zoneId(); LocalDate day = clock.today();
        long begin = day.atStartOfDay(zone).toInstant().toEpochMilli();
        long end = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
        Uri.Builder builder = CalendarContract.Instances.CONTENT_URI.buildUpon();
        ContentUris.appendId(builder, begin); ContentUris.appendId(builder, end);
        String[] columns = { CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.ALL_DAY };
        String selection = CalendarContract.Instances.VISIBLE + "=1 AND "
                + CalendarContract.Instances.STATUS + "!=" + CalendarContract.Events.STATUS_CANCELED + " AND ("
                + CalendarContract.Instances.SELF_ATTENDEE_STATUS + " IS NULL OR "
                + CalendarContract.Instances.SELF_ATTENDEE_STATUS + "!=" + CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED
                + ") AND " + CalendarContract.Calendars.ACCOUNT_TYPE + "=?";
        List<CalendarEventSnapshot> result = new ArrayList<>();
        try (Cursor cursor = context.getContentResolver().query(builder.build(), columns, selection,
                new String[]{"com.google"}, CalendarContract.Instances.BEGIN + " ASC")) {
            if (cursor == null) return result;
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");
            while (cursor.moveToNext()) {
                String title = cursor.getString(0); long start = cursor.getLong(1); boolean allDay = cursor.getInt(2) != 0;
                java.time.ZonedDateTime local = Instant.ofEpochMilli(start).atZone(zone);
                result.add(new CalendarEventSnapshot(allDay ? "ganztägig" : local.format(formatter),
                        title == null || title.trim().isEmpty() ? "Termin" : title, allDay ? 0 : local.getHour()*60+local.getMinute()));
            }
        } catch (RuntimeException error) {
            logger.error(TAG, "Calendar provider query failed", error);
            return Collections.emptyList();
        }
        result.sort(Comparator.comparingInt(event -> event.minuteOfDay)); return result;
    }
}
