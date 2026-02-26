package com.autosecretary.features.meal.data.internal.mapper;

import com.autosecretary.features.meal.data.mapper.MealPlanStorageMapper;
import com.autosecretary.features.meal.domain.MealPlan;

import java.util.Map;

public class MealPlanRowMapper implements RowMapper<MealPlan> {
    @Override
    public Map<String, Object> toRow(MealPlan mealPlan) {
        return MealPlanStorageMapper.toStorageRow(mealPlan);
    }

    @Override
    public MealPlan fromRow(Map<String, Object> row) {
        return MealPlanStorageMapper.fromStorageRow(row);
    }
}
