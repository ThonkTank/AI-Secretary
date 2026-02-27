package com.autosecretary.features.meal.data.internal.mapper;

import com.autosecretary.features.meal.data.internal.MealFieldKeys;
import com.autosecretary.features.meal.domain.MealPlan;
import com.autosecretary.features.meal.domain.MealType;

import java.util.HashMap;
import java.util.Map;

public class MealPlanRowMapper implements RowMapper<MealPlan> {
    @Override
    public Map<String, Object> toRow(MealPlan mealPlan) {
        Map<String, Object> row = new HashMap<>();
        row.put(MealFieldKeys.MealPlan.ID, mealPlan.id);
        row.put(MealFieldKeys.MealPlan.DATE, MapperSupport.toDateString(mealPlan.date));
        row.put(MealFieldKeys.MealPlan.MEAL_TYPE, MapperSupport.enumNameOrNull(mealPlan.mealType));
        row.put(MealFieldKeys.MealPlan.RECIPE_ID, mealPlan.recipeId);
        row.put(MealFieldKeys.MealPlan.PLANNED_SERVINGS, mealPlan.plannedServings);
        row.put(MealFieldKeys.MealPlan.IS_COMPLETED, mealPlan.isCompleted ? 1 : 0);
        row.put(MealFieldKeys.MealPlan.ACTUAL_SERVINGS, mealPlan.actualServings);
        row.put(MealFieldKeys.MealPlan.COMPLETED_AT, MapperSupport.toDateTimeString(mealPlan.completedAt));
        row.put(MealFieldKeys.MealPlan.ITEM_ID, mealPlan.itemId);
        row.put(MealFieldKeys.MealPlan.RECIPE_TITLE, mealPlan.recipeTitle);
        row.put(MealFieldKeys.MealPlan.ESTIMATED_CALORIES, mealPlan.estimatedCalories);
        return row;
    }

    @Override
    public MealPlan fromRow(Map<String, Object> row) {
        MealPlan mealPlan = new MealPlan();
        mealPlan.id = MapperSupport.asNullableLong(row.get(MealFieldKeys.MealPlan.ID));
        mealPlan.date = MapperSupport.asLocalDate(row.get(MealFieldKeys.MealPlan.DATE));
        mealPlan.mealType = MapperSupport.asEnum(MealType.class, row.get(MealFieldKeys.MealPlan.MEAL_TYPE), null);
        mealPlan.recipeId = MapperSupport.asLong(row.get(MealFieldKeys.MealPlan.RECIPE_ID));
        mealPlan.plannedServings = MapperSupport.asInt(row.get(MealFieldKeys.MealPlan.PLANNED_SERVINGS));
        mealPlan.isCompleted = MapperSupport.asBoolean(row.get(MealFieldKeys.MealPlan.IS_COMPLETED));
        mealPlan.actualServings = MapperSupport.asInt(row.get(MealFieldKeys.MealPlan.ACTUAL_SERVINGS));
        mealPlan.completedAt = MapperSupport.asLocalDateTime(row.get(MealFieldKeys.MealPlan.COMPLETED_AT));
        mealPlan.itemId = MapperSupport.asNullableLong(row.get(MealFieldKeys.MealPlan.ITEM_ID));
        mealPlan.recipeTitle = (String) row.get(MealFieldKeys.MealPlan.RECIPE_TITLE);
        mealPlan.estimatedCalories = MapperSupport.asInt(row.get(MealFieldKeys.MealPlan.ESTIMATED_CALORIES));
        return mealPlan;
    }
}
