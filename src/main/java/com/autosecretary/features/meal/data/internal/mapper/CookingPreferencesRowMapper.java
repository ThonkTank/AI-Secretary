package com.autosecretary.features.meal.data.internal.mapper;

import com.autosecretary.features.meal.data.internal.MealFieldKeys;
import com.autosecretary.features.meal.domain.CookingPreferences;

import java.util.HashMap;
import java.util.Map;

/**
 * {@link RowMapper} for {@link CookingPreferences}.
 *
 * <p>{@code CookingPreferences} is a singleton entity — only one row (id=1) is ever
 * persisted. This constraint is enforced by {@code StorageMealRepository.saveCookingPreferences()},
 * not by this mapper.
 *
 * <p>The four {@code *CookingDays} fields ({@link java.time.DayOfWeek} sets) are serialized
 * as comma-separated enum names via {@link MapperSupport#serializeEnumSet} and
 * deserialized via {@link MapperSupport#asDayOfWeekSet}.
 */
public class CookingPreferencesRowMapper implements RowMapper<CookingPreferences> {
    @Override
    public Map<String, Object> toRow(CookingPreferences preferences) {
        Map<String, Object> row = new HashMap<>();
        row.put(MealFieldKeys.CookingPreferences.ID, preferences.id);
        row.put(MealFieldKeys.CookingPreferences.MAX_BREAKFAST_COOKING, preferences.maxBreakfastCooking);
        row.put(MealFieldKeys.CookingPreferences.MAX_LUNCH_COOKING, preferences.maxLunchCooking);
        row.put(MealFieldKeys.CookingPreferences.MAX_DINNER_COOKING, preferences.maxDinnerCooking);
        row.put(MealFieldKeys.CookingPreferences.MAX_SNACK_COOKING, preferences.maxSnackCooking);
        row.put(MealFieldKeys.CookingPreferences.BREAKFAST_COOKING_DAYS, MapperSupport.serializeEnumSet(preferences.breakfastCookingDays));
        row.put(MealFieldKeys.CookingPreferences.LUNCH_COOKING_DAYS, MapperSupport.serializeEnumSet(preferences.lunchCookingDays));
        row.put(MealFieldKeys.CookingPreferences.DINNER_COOKING_DAYS, MapperSupport.serializeEnumSet(preferences.dinnerCookingDays));
        row.put(MealFieldKeys.CookingPreferences.SNACK_COOKING_DAYS, MapperSupport.serializeEnumSet(preferences.snackCookingDays));
        row.put(MealFieldKeys.CookingPreferences.QUICK_PREP_MAX_MINUTES, preferences.quickPrepMaxMinutes);
        return row;
    }

    @Override
    public CookingPreferences fromRow(Map<String, Object> row) {
        CookingPreferences preferences = new CookingPreferences();
        preferences.id = MapperSupport.asNullableLong(row.get(MealFieldKeys.CookingPreferences.ID));
        preferences.maxBreakfastCooking = MapperSupport.asInt(row.get(MealFieldKeys.CookingPreferences.MAX_BREAKFAST_COOKING));
        preferences.maxLunchCooking = MapperSupport.asInt(row.get(MealFieldKeys.CookingPreferences.MAX_LUNCH_COOKING));
        preferences.maxDinnerCooking = MapperSupport.asInt(row.get(MealFieldKeys.CookingPreferences.MAX_DINNER_COOKING));
        preferences.maxSnackCooking = MapperSupport.asInt(row.get(MealFieldKeys.CookingPreferences.MAX_SNACK_COOKING));
        preferences.breakfastCookingDays = MapperSupport.asDayOfWeekSet(row.get(MealFieldKeys.CookingPreferences.BREAKFAST_COOKING_DAYS));
        preferences.lunchCookingDays = MapperSupport.asDayOfWeekSet(row.get(MealFieldKeys.CookingPreferences.LUNCH_COOKING_DAYS));
        preferences.dinnerCookingDays = MapperSupport.asDayOfWeekSet(row.get(MealFieldKeys.CookingPreferences.DINNER_COOKING_DAYS));
        preferences.snackCookingDays = MapperSupport.asDayOfWeekSet(row.get(MealFieldKeys.CookingPreferences.SNACK_COOKING_DAYS));
        preferences.quickPrepMaxMinutes = MapperSupport.asInt(row.get(MealFieldKeys.CookingPreferences.QUICK_PREP_MAX_MINUTES));
        return preferences;
    }
}
