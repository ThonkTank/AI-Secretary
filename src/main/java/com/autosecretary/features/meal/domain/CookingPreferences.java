package com.autosecretary.features.meal.domain;

import java.time.DayOfWeek;
import java.util.EnumSet;
import java.util.Set;

/**
 * Per-meal-type cooking schedule configuration: how often and on which days cooking is allowed.
 *
 * <p>For each {@link MealType}, two constraints are stored:
 * <ul>
 *   <li>{@code max*Cooking} — maximum number of cooking sessions per week for that meal type.</li>
 *   <li>{@code *CookingDays} — set of days on which cooking for that meal type is permitted.</li>
 * </ul>
 *
 * <p>The no-arg constructor initialises sensible defaults: max=3 cooking sessions/week for
 * BREAKFAST, LUNCH, and DINNER (all days allowed); SNACK defaults to 0 sessions (no days).
 *
 * <p>Shopping days are managed separately by the task feature's checklist system and are
 * not part of this object.
 *
 * <p>This is a singleton in storage — only one row (id=1) is ever persisted.
 * {@code MealRepository#getCookingPreferences()} returns a default instance when none exists.
 */
public class CookingPreferences {

    public Long id;
    public int maxBreakfastCooking;     // max cooking sessions per week for BREAKFAST
    public int maxLunchCooking;
    public int maxDinnerCooking;
    public int maxSnackCooking;
    public Set<DayOfWeek> breakfastCookingDays;
    public Set<DayOfWeek> lunchCookingDays;
    public Set<DayOfWeek> dinnerCookingDays;
    public Set<DayOfWeek> snackCookingDays;
    public int quickPrepMaxMinutes;     // recipes with totalTime <= this value don't count as a "cooking session" (default 15)

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
