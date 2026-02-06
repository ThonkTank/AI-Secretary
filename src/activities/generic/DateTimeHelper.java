package activities.generic;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;
import java.util.Locale;

/**
 * Statische Helfer-Methoden fuer Datum/Zeit-Operationen.
 * Zentralisiert WeekFields-Handling fuer deutsche Wochenberechnung.
 */
public final class DateTimeHelper {

    private static final WeekFields GERMANY_WEEK_FIELDS = WeekFields.of(Locale.GERMANY);

    private DateTimeHelper() {}

    /** Montag der Woche fuer ein gegebenes Datum */
    public static LocalDate getMonday(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    /** Wochen-Key im Format "2026-W05" */
    public static String getWeekKey(LocalDate weekStart) {
        int week = weekStart.get(GERMANY_WEEK_FIELDS.weekOfWeekBasedYear());
        int year = weekStart.get(GERMANY_WEEK_FIELDS.weekBasedYear());
        return String.format("%d-W%02d", year, week);
    }

    /** Formatiert LocalTime als "HH:mm" */
    public static String formatTime(LocalTime time) {
        return String.format("%02d:%02d", time.getHour(), time.getMinute());
    }

    /** Extrahiert Wochennummer aus einem Datum (deutsche Wochenberechnung) */
    public static int getWeekNumber(LocalDate date) {
        return date.get(GERMANY_WEEK_FIELDS.weekOfWeekBasedYear());
    }
}
