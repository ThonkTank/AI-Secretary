package com.autosecretary.features.meal.data.mapper;

import com.autosecretary.features.meal.domain.MealPlan;

import java.util.Map;

/**
 * Kompatibilitäts-Fassade, delegiert auf MealPlanStorageMapper.
 */
public final class MealPlanParser {
    private MealPlanParser() {
    }

    public static MealPlan parse(Map<String, Object> row) {
        return MealPlanStorageMapper.fromStorageRow(row);
    }

    public static Map<String, Object> serialize(MealPlan mealPlan) {
        return MealPlanStorageMapper.toStorageRow(mealPlan);
    }
}
