package entities;

import java.time.DayOfWeek;
import java.time.LocalTime;

/**
 * MealSchedule - Mahlzeiten-Kalender
 *
 * Definiert WANN gegessen wird (nicht WAS).
 * 7 Tage × 4 Mahlzeiten = max 28 Einträge.
 *
 * Beispiel:
 * - (MONDAY, BREAKFAST, 06:30, true)   → Frühstück Mo um 06:30
 * - (SATURDAY, BREAKFAST, 09:00, true) → Frühstück Sa um 09:00 (Wochenende später)
 * - (MONDAY, SNACK, null, false)       → Kein Snack am Montag
 */
public class MealSchedule {

    public Long id;
    public DayOfWeek dayOfWeek;      // MONDAY, TUESDAY, ...
    public MealType mealType;        // BREAKFAST, LUNCH, DINNER, SNACK
    public LocalTime scheduledTime;  // z.B. 06:30 (null = keine feste Zeit)
    public boolean isEnabled;        // false = "—" im Kalender

    // ============================================================================
    // CONSTRUCTORS
    // ============================================================================

    public MealSchedule() {
        this.isEnabled = true;
    }

    public MealSchedule(DayOfWeek day, MealType type, LocalTime time, boolean enabled) {
        this.dayOfWeek = day;
        this.mealType = type;
        this.scheduledTime = time;
        this.isEnabled = enabled;
    }

    // ============================================================================
    // BUILDER
    // ============================================================================

    public static class Builder {
        private DayOfWeek dayOfWeek;
        private MealType mealType;
        private LocalTime scheduledTime;
        private boolean isEnabled = true;

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

        public Builder enabled(boolean enabled) {
            this.isEnabled = enabled;
            return this;
        }

        public Builder disabled() {
            this.isEnabled = false;
            return this;
        }

        public MealSchedule build() {
            return new MealSchedule(dayOfWeek, mealType, scheduledTime, isEnabled);
        }
    }

    // ============================================================================
    // UTILITY METHODS
    // ============================================================================

    /**
     * Formatierte Zeit für Anzeige.
     * @return "06:30" oder "—" wenn deaktiviert/keine Zeit
     */
    public String getFormattedTime() {
        if (!isEnabled || scheduledTime == null) {
            return "—";
        }
        return String.format("%02d:%02d", scheduledTime.getHour(), scheduledTime.getMinute());
    }

    /**
     * Kurzes Label für den Wochentag.
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
            dayOfWeek + " " + mealType + " " + getFormattedTime() +
            (isEnabled ? "" : " [disabled]") +
            "}";
    }
}
