package com.autosecretary.features.meal.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.time.DayOfWeek;
import java.util.Set;

/**
 * Singleton entity holding the household's cooking-time constraints per meal type.
 *
 * Only one row ever exists in this table; the PK is always "1". Reading or upserting
 * should use this fixed id. {@code maxBreakfastCooking} and siblings are the maximum
 * number of minutes the household is willing to spend cooking for that meal type on a
 * given day. The {@code *CookingDays} sets define which days of the week cooking is
 * permitted for that meal type — stored as comma-separated {@code DayOfWeek} names via
 * the existing {@code Set<DayOfWeek>} converter.
 */
@Entity(tableName = "cooking_preferences")
public class MealCookingPreferencesEntity {

    @PrimaryKey
    @NonNull
    public String id = "1";

    public int maxBreakfastCooking;

    public int maxLunchCooking;

    public int maxDinnerCooking;

    public int maxSnackCooking;

    public Set<DayOfWeek> breakfastCookingDays;

    public Set<DayOfWeek> lunchCookingDays;

    public Set<DayOfWeek> dinnerCookingDays;

    public Set<DayOfWeek> snackCookingDays;

    public int quickPrepMaxMinutes;
}
