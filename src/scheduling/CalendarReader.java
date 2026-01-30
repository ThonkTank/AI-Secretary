package usecases.dailyPlanning;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;
import entities.CalendarEvent;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Liest Kalender-Events vom lokalen Geraet fuer einen bestimmten Tag.
 * Nutzt CalendarContract.Instances fuer zeitbasierte Abfragen.
 */
public class CalendarReader {

    // CalendarEvent ist jetzt in eigener Datei: entities.CalendarEvent

    /**
     * Liest alle Events fuer den gegebenen Tag und gibt sie als sortierte Liste zurueck.
     * Ganztaegige Events werden ignoriert.
     * Events werden auf das Schedule-Fenster geclampt.
     *
     * @param context        Android Context fuer ContentResolver
     * @param day            Der Tag fuer den Events geladen werden
     * @param scheduleStart  Beginn des Planungsfensters
     * @param scheduleEnd    Ende des Planungsfensters
     * @return Sortierte Liste von CalendarEvents innerhalb des Schedule-Fensters
     */
    public static List<CalendarEvent> getEventsForDay(
            Context context, LocalDate day,
            LocalTime scheduleStart, LocalTime scheduleEnd) {

        // Permission-Check: ohne Berechtigung leere Liste
        if (context.checkSelfPermission(android.Manifest.permission.READ_CALENDAR)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return new ArrayList<>();
        }

        // Zeitfenster: Tagesanfang bis Tagesende in Millis
        ZoneId zone = ZoneId.systemDefault();
        long dayStartMillis = day.atStartOfDay(zone).toInstant().toEpochMilli();
        long dayEndMillis = day.atTime(23, 59, 59).atZone(zone).toInstant().toEpochMilli();

        // CalendarContract.Instances URI mit Start/Ende
        Uri.Builder builder = CalendarContract.Instances.CONTENT_URI.buildUpon();
        ContentUris.appendId(builder, dayStartMillis);
        ContentUris.appendId(builder, dayEndMillis);

        String[] projection = {
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY
        };

        List<CalendarEvent> events = new ArrayList<>();

        Cursor cursor = context.getContentResolver().query(
            builder.build(), projection, null, null,
            CalendarContract.Instances.BEGIN + " ASC");

        if (cursor == null) return events;

        try {
            while (cursor.moveToNext()) {
                String title = cursor.getString(0);
                long beginMillis = cursor.getLong(1);
                long endMillis = cursor.getLong(2);
                boolean allDay = cursor.getInt(3) != 0;

                // Ganztaegige Events ignorieren
                if (allDay) continue;

                // Millis zu LocalTime konvertieren
                LocalTime eventStart = Instant.ofEpochMilli(beginMillis)
                        .atZone(zone).toLocalTime();
                LocalTime eventEnd = Instant.ofEpochMilli(endMillis)
                        .atZone(zone).toLocalTime();

                // Events ausserhalb des Schedule-Fensters ignorieren
                if (!eventStart.isBefore(scheduleEnd) || !eventEnd.isAfter(scheduleStart)) {
                    continue;
                }

                // Auf Schedule-Fenster clampen
                if (eventStart.isBefore(scheduleStart)) eventStart = scheduleStart;
                if (eventEnd.isAfter(scheduleEnd)) eventEnd = scheduleEnd;

                if (title == null || title.isEmpty()) title = "Termin";

                events.add(new CalendarEvent(title, eventStart, eventEnd));
            }
        } finally {
            cursor.close();
        }

        return events;
    }
}
