package com.autosecretary.features.meal.domain;

import java.time.DayOfWeek;
import java.util.EnumSet;
import java.util.Set;

/**
 * Kochsession-Konfiguration pro MealType.
 * Bestimmt wann und wie oft pro Woche gekocht wird.
 * Einkaufstage werden separat als TrackedItems verwaltet.
 */
public class CookingPreferences {

    public Long id;
    public int maxBreakfastCooking;     // Koch-Sessions pro Woche
    public int maxLunchCooking;
    public int maxDinnerCooking;
    public int maxSnackCooking;
    public Set<DayOfWeek> breakfastCookingDays;
    public Set<DayOfWeek> lunchCookingDays;
    public Set<DayOfWeek> dinnerCookingDays;
    public Set<DayOfWeek> snackCookingDays;
    public int quickPrepMaxMinutes;     // Grenze fuer "kein Kochen noetig" (Default 15)

    public CookingPreferences() {
        maxBreakfastCooking = 3;
        maxLunchCooking = 3;
        maxDinnerCooking = 3;
        maxSnackCooking = 0;
        breakfastCookingDays = EnumSet.allOf(DayOfWeek.class);
        lunchCookingDays = EnumSet.allOf(DayOfWeek.class);
        dinnerCookingDays = EnumSet.allOf(DayOfWeek.class);
        snackCookingDays = EnumSet.noneOf(DayOfWeek.class);
        quickPrepMaxMinutes = 15;
    }

    public int getMaxCookingPerWeek(MealType type) {
        return switch (type) {
            case BREAKFAST -> maxBreakfastCooking;
            case LUNCH -> maxLunchCooking;
            case DINNER -> maxDinnerCooking;
            case SNACK -> maxSnackCooking;
        };
    }

    public Set<DayOfWeek> getAllowedCookingDays(MealType type) {
        return switch (type) {
            case BREAKFAST -> breakfastCookingDays;
            case LUNCH -> lunchCookingDays;
            case DINNER -> dinnerCookingDays;
            case SNACK -> snackCookingDays;
        };
    }

    public boolean canCookOn(DayOfWeek day, MealType type) {
        return getAllowedCookingDays(type).contains(day);
    }
}
