package entities;

import java.time.DayOfWeek;
import java.util.EnumSet;
import java.util.Set;

/**
 * Koch-Präferenzen für den Haushalt.
 * Definiert wie oft pro Woche gekocht werden darf und an welchen Tagen.
 * Singleton-Tabelle (nur 1 Eintrag pro App).
 */
public class CookingPreferences {

    public Long id;

    // Max. Kochvorgänge pro Woche pro Mahlzeittyp
    public int maxBreakfastCookingPerWeek;     // z.B. 2 (nur Wochenende)
    public int maxLunchCookingPerWeek;         // z.B. 3
    public int maxDinnerCookingPerWeek;        // z.B. 3

    // Erlaubte Kochtage pro Mahlzeittyp
    public Set<DayOfWeek> breakfastCookingDays;  // z.B. {SATURDAY, SUNDAY}
    public Set<DayOfWeek> lunchCookingDays;      // z.B. {MONDAY, WEDNESDAY, FRIDAY}
    public Set<DayOfWeek> dinnerCookingDays;     // z.B. {SUNDAY, TUESDAY, THURSDAY}

    // Max. Zeit für "schnelle" Gerichte (in Minuten)
    public int quickPrepMaxMinutes;            // z.B. 15 (Sandwich, Joghurt, Spiegelei)

    // Builder
    public static class Builder {
        private final CookingPreferences prefs = new CookingPreferences();

        public Builder() {
            // Defaults
            prefs.maxBreakfastCookingPerWeek = 2;
            prefs.maxLunchCookingPerWeek = 3;
            prefs.maxDinnerCookingPerWeek = 3;
            prefs.breakfastCookingDays = EnumSet.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);
            prefs.lunchCookingDays = EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY);
            prefs.dinnerCookingDays = EnumSet.of(DayOfWeek.SUNDAY, DayOfWeek.TUESDAY, DayOfWeek.THURSDAY);
            prefs.quickPrepMaxMinutes = 15;
        }

        public Builder maxBreakfastCooking(int v) { prefs.maxBreakfastCookingPerWeek = v; return this; }
        public Builder maxLunchCooking(int v) { prefs.maxLunchCookingPerWeek = v; return this; }
        public Builder maxDinnerCooking(int v) { prefs.maxDinnerCookingPerWeek = v; return this; }
        public Builder breakfastDays(Set<DayOfWeek> v) { prefs.breakfastCookingDays = v; return this; }
        public Builder lunchDays(Set<DayOfWeek> v) { prefs.lunchCookingDays = v; return this; }
        public Builder dinnerDays(Set<DayOfWeek> v) { prefs.dinnerCookingDays = v; return this; }
        public Builder quickPrepMax(int minutes) { prefs.quickPrepMaxMinutes = minutes; return this; }

        public CookingPreferences build() { return prefs; }
    }

    // ============== UTILITY ==============

    /**
     * Prüft ob an einem bestimmten Tag für einen Mahlzeittyp gekocht werden darf.
     */
    public boolean canCookOn(DayOfWeek day, MealType mealType) {
        return switch (mealType) {
            case BREAKFAST -> breakfastCookingDays != null && breakfastCookingDays.contains(day);
            case LUNCH -> lunchCookingDays != null && lunchCookingDays.contains(day);
            case DINNER -> dinnerCookingDays != null && dinnerCookingDays.contains(day);
            case SNACK -> true;  // Snacks haben keine Kochtage-Einschränkung
        };
    }

    /**
     * Gibt das Maximum an Kochvorgängen pro Woche für einen Mahlzeittyp zurück.
     */
    public int getMaxCookingFor(MealType mealType) {
        return switch (mealType) {
            case BREAKFAST -> maxBreakfastCookingPerWeek;
            case LUNCH -> maxLunchCookingPerWeek;
            case DINNER -> maxDinnerCookingPerWeek;
            case SNACK -> Integer.MAX_VALUE;  // Snacks unbegrenzt
        };
    }

}
