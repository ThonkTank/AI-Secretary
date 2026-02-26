package com.autosecretary.features.meal.data.internal.mapper;

import com.autosecretary.features.meal.domain.MealPlan;
import com.autosecretary.features.meal.domain.MealType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

public class MealPlanRowMapper implements RowMapper<MealPlan> {
    @Override
    public Map<String, Object> toRow(MealPlan mealPlan) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", mealPlan.id);
        row.put("date", mealPlan.date == null ? null : mealPlan.date.toString());
        row.put("mealType", mealPlan.mealType == null ? null : mealPlan.mealType.name());
        row.put("recipeId", mealPlan.recipeId);
        row.put("plannedServings", mealPlan.plannedServings);
        row.put("isCompleted", mealPlan.isCompleted);
        row.put("actualServings", mealPlan.actualServings);
        row.put("completedAt", mealPlan.completedAt == null ? null : mealPlan.completedAt.toString());
        row.put("itemId", mealPlan.itemId);
        row.put("recipeTitle", mealPlan.recipeTitle);
        row.put("estimatedCalories", mealPlan.estimatedCalories);
        return row;
    }

    @Override
    public MealPlan fromRow(Map<String, Object> row) {
        MealPlan mealPlan = new MealPlan();
        mealPlan.id = MapperSupport.asNullableLong(row.get("id"));
        String date = (String) row.get("date");
        mealPlan.date = date == null ? null : LocalDate.parse(date);
        String mealType = (String) row.get("mealType");
        mealPlan.mealType = mealType == null ? null : MealType.valueOf(mealType);
        mealPlan.recipeId = MapperSupport.asLong(row.get("recipeId"));
        mealPlan.plannedServings = MapperSupport.asInt(row.get("plannedServings"));
        mealPlan.isCompleted = Boolean.TRUE.equals(MapperSupport.asBoolean(row.get("isCompleted")));
        mealPlan.actualServings = MapperSupport.asInt(row.get("actualServings"));
        String completedAt = (String) row.get("completedAt");
        mealPlan.completedAt = completedAt == null ? null : LocalDateTime.parse(completedAt);
        mealPlan.itemId = MapperSupport.asNullableLong(row.get("itemId"));
        mealPlan.recipeTitle = (String) row.get("recipeTitle");
        mealPlan.estimatedCalories = MapperSupport.asInt(row.get("estimatedCalories"));
        return mealPlan;
    }
}
