package com.autosecretary.features.meal.data.mapper;

import com.autosecretary.features.meal.domain.MealPlan;
import com.autosecretary.features.meal.domain.MealType;

import java.util.HashMap;
import java.util.Map;

public final class MealPlanStorageMapper {
    private MealPlanStorageMapper() {
    }

    public static MealPlan fromStorageRow(Map<String, Object> row) {
        MealPlan mealPlan = new MealPlan();
        mealPlan.id = LegacyMapperSupport.asNullableLong(LegacyMapperSupport.get(row, LegacyMealFieldKeys.MealPlan.ID, null));
        mealPlan.date = LegacyMapperSupport.asLocalDate(LegacyMapperSupport.get(row, LegacyMealFieldKeys.MealPlan.DATE, "date"));
        mealPlan.mealType = LegacyMapperSupport.asEnum(MealType.class,
                LegacyMapperSupport.get(row, LegacyMealFieldKeys.MealPlan.MEAL_TYPE, "mealType"), null);
        mealPlan.recipeId = LegacyMapperSupport.asLong(LegacyMapperSupport.get(row, LegacyMealFieldKeys.MealPlan.RECIPE_ID, "recipeId"), 0L);
        mealPlan.plannedServings = LegacyMapperSupport.asInt(LegacyMapperSupport.get(row, LegacyMealFieldKeys.MealPlan.PLANNED_SERVINGS, "plannedServings"), 0);
        mealPlan.isCompleted = LegacyMapperSupport.asBoolean(LegacyMapperSupport.get(row, LegacyMealFieldKeys.MealPlan.IS_COMPLETED, "isCompleted"), false);
        mealPlan.actualServings = LegacyMapperSupport.asInt(LegacyMapperSupport.get(row, LegacyMealFieldKeys.MealPlan.ACTUAL_SERVINGS, "actualServings"), 0);
        mealPlan.completedAt = LegacyMapperSupport.asLocalDateTime(LegacyMapperSupport.get(row, LegacyMealFieldKeys.MealPlan.COMPLETED_AT, "completedAt"));
        mealPlan.itemId = LegacyMapperSupport.asNullableLong(LegacyMapperSupport.get(row, LegacyMealFieldKeys.MealPlan.ITEM_ID, "itemId"));
        mealPlan.recipeTitle = (String) LegacyMapperSupport.get(row, LegacyMealFieldKeys.MealPlan.RECIPE_TITLE, "recipeTitle");
        mealPlan.estimatedCalories = LegacyMapperSupport.asInt(LegacyMapperSupport.get(row, LegacyMealFieldKeys.MealPlan.ESTIMATED_CALORIES, "estimatedCalories"), 0);
        return mealPlan;
    }

    public static Map<String, Object> toStorageRow(MealPlan mealPlan) {
        Map<String, Object> row = new HashMap<>();
        row.put(LegacyMealFieldKeys.MealPlan.ID, mealPlan.id);
        row.put(LegacyMealFieldKeys.MealPlan.DATE, mealPlan.date == null ? null : mealPlan.date.toString());
        row.put(LegacyMealFieldKeys.MealPlan.MEAL_TYPE, mealPlan.mealType == null ? null : mealPlan.mealType.name());
        row.put(LegacyMealFieldKeys.MealPlan.RECIPE_ID, mealPlan.recipeId);
        row.put(LegacyMealFieldKeys.MealPlan.PLANNED_SERVINGS, mealPlan.plannedServings);
        row.put(LegacyMealFieldKeys.MealPlan.IS_COMPLETED, mealPlan.isCompleted ? 1 : 0);
        row.put(LegacyMealFieldKeys.MealPlan.ACTUAL_SERVINGS, mealPlan.actualServings);
        row.put(LegacyMealFieldKeys.MealPlan.COMPLETED_AT, mealPlan.completedAt == null ? null : mealPlan.completedAt.toString());
        row.put(LegacyMealFieldKeys.MealPlan.ITEM_ID, mealPlan.itemId);
        row.put(LegacyMealFieldKeys.MealPlan.RECIPE_TITLE, mealPlan.recipeTitle);
        row.put(LegacyMealFieldKeys.MealPlan.ESTIMATED_CALORIES, mealPlan.estimatedCalories);
        return row;
    }
}
