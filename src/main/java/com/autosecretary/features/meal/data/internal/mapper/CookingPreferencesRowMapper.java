package com.autosecretary.features.meal.data.internal.mapper;

import com.autosecretary.features.meal.domain.CookingPreferences;

import java.util.HashMap;
import java.util.Map;

public class CookingPreferencesRowMapper implements RowMapper<CookingPreferences> {
    @Override
    public Map<String, Object> toRow(CookingPreferences preferences) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", preferences.id);
        row.put("maxBreakfastCooking", preferences.maxBreakfastCooking);
        row.put("maxLunchCooking", preferences.maxLunchCooking);
        row.put("maxDinnerCooking", preferences.maxDinnerCooking);
        row.put("maxSnackCooking", preferences.maxSnackCooking);
        row.put("breakfastCookingDays", preferences.breakfastCookingDays);
        row.put("lunchCookingDays", preferences.lunchCookingDays);
        row.put("dinnerCookingDays", preferences.dinnerCookingDays);
        row.put("snackCookingDays", preferences.snackCookingDays);
        row.put("quickPrepMaxMinutes", preferences.quickPrepMaxMinutes);
        return row;
    }

    @Override
    public CookingPreferences fromRow(Map<String, Object> row) {
        CookingPreferences preferences = new CookingPreferences();
        preferences.id = MapperSupport.asNullableLong(row.get("id"));
        preferences.maxBreakfastCooking = MapperSupport.asInt(row.get("maxBreakfastCooking"));
        preferences.maxLunchCooking = MapperSupport.asInt(row.get("maxLunchCooking"));
        preferences.maxDinnerCooking = MapperSupport.asInt(row.get("maxDinnerCooking"));
        preferences.maxSnackCooking = MapperSupport.asInt(row.get("maxSnackCooking"));
        preferences.breakfastCookingDays = MapperSupport.asSet(row.get("breakfastCookingDays"));
        preferences.lunchCookingDays = MapperSupport.asSet(row.get("lunchCookingDays"));
        preferences.dinnerCookingDays = MapperSupport.asSet(row.get("dinnerCookingDays"));
        preferences.snackCookingDays = MapperSupport.asSet(row.get("snackCookingDays"));
        preferences.quickPrepMaxMinutes = MapperSupport.asInt(row.get("quickPrepMaxMinutes"));
        return preferences;
    }
}
