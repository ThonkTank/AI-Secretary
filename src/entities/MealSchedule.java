package entities;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

/**
 * MealSchedule - Mahlzeiten-Kalender
 *
 * Definiert WANN und WIE LANGE gegessen wird (nicht WAS).
 * Beliebig viele Eintraege pro Tag moeglich (kein UNIQUE-Constraint).
 *
 * Beispiel:
 * - (MONDAY, BREAKFAST, 06:30, 30)  → Fruehstueck Mo 06:30-07:00
 * - (MONDAY, LUNCH, 12:00, 45)      → Mittagessen Mo 12:00-12:45
 * - (MONDAY, LUNCH, 13:00, 30)      → Zweites Mittagessen Mo 13:00-13:30
 */
public class MealSchedule {

    public Long id;
    public DayOfWeek dayOfWeek;      // MONDAY, TUESDAY, ...
    public MealType mealType;        // BREAKFAST, LUNCH, DINNER, SNACK
    public LocalTime scheduledTime;  // z.B. 06:30 (null = keine feste Zeit)
    public int durationMinutes;      // Standard: 30 Minuten

    public static final int DEFAULT_DURATION_MINUTES = 30;

    // ============================================================================
    // CONSTRUCTORS
    // ============================================================================

    public MealSchedule() {
        this.durationMinutes = DEFAULT_DURATION_MINUTES;
    }

    public MealSchedule(DayOfWeek day, MealType type, LocalTime time, int durationMinutes) {
        this.dayOfWeek = day;
        this.mealType = type;
        this.scheduledTime = time;
        this.durationMinutes = durationMinutes;
    }

    // ============================================================================
    // BUILDER
    // ============================================================================

    public static class Builder {
        private DayOfWeek dayOfWeek;
        private MealType mealType;
        private LocalTime scheduledTime;
        private int durationMinutes = DEFAULT_DURATION_MINUTES;

        public Builder(DayOfWeek day, MealType type) {
            this.dayOfWeek = day;
            this.mealType = type;
        }

        public Builder time(LocalTime time) {
            this.scheduledTime = time;
            return this;
        }

        public Builder time(int hour, int minute) {
            this.scheduledTime = LocalTime.of(hour, minute);
            return this;
        }

        public Builder duration(int minutes) {
            this.durationMinutes = minutes;
            return this;
        }

        public MealSchedule build() {
            return new MealSchedule(dayOfWeek, mealType, scheduledTime, durationMinutes);
        }
    }

    // ============================================================================
    // UTILITY METHODS
    // ============================================================================

    /**
     * Sortiert MealSchedules nach Tag (Mo-So) dann Zeit (null ans Ende).
     */
    public static void sortByTime(List<MealSchedule> list) {
        list.sort((a, b) -> {
            int dayCompare = a.dayOfWeek.compareTo(b.dayOfWeek);
            if (dayCompare != 0) return dayCompare;
            if (a.scheduledTime == null) return 1;
            if (b.scheduledTime == null) return -1;
            return a.scheduledTime.compareTo(b.scheduledTime);
        });
    }

    /**
     * Formatierte Startzeit fuer Anzeige.
     * @return "06:30" oder "—" wenn keine Zeit
     */
    public String getFormattedTime() {
        if (scheduledTime == null) {
            return "—";
        }
        return String.format("%02d:%02d", scheduledTime.getHour(), scheduledTime.getMinute());
    }

    /**
     * Berechnet die Endzeit basierend auf Startzeit + Dauer.
     * @return Endzeit oder null wenn keine Startzeit
     */
    public LocalTime getEndTime() {
        if (scheduledTime == null) return null;
        return scheduledTime.plusMinutes(durationMinutes);
    }

    /**
     * Formatierter Zeitraum fuer Anzeige.
     * @return "06:30 - 07:00" oder "—"
     */
    public String getFormattedTimeRange() {
        if (scheduledTime == null) return "—";
        LocalTime end = getEndTime();
        return String.format("%02d:%02d - %02d:%02d",
            scheduledTime.getHour(), scheduledTime.getMinute(),
            end.getHour(), end.getMinute());
    }

    /**
     * Kurzes Label fuer den Wochentag.
     * @return "Mo", "Di", "Mi", etc.
     */
    public String getDayLabel() {
        return switch (dayOfWeek) {
            case MONDAY -> "Mo";
            case TUESDAY -> "Di";
            case WEDNESDAY -> "Mi";
            case THURSDAY -> "Do";
            case FRIDAY -> "Fr";
            case SATURDAY -> "Sa";
            case SUNDAY -> "So";
        };
    }

    @Override
    public String toString() {
        return "MealSchedule{" +
            dayOfWeek + " " + mealType + " " + getFormattedTimeRange() +
            " (" + durationMinutes + "min)" +
            "}";
    }
}
