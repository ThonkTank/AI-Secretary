package com.autosecretary.features.meal.data.internal.dao;

import com.autosecretary.features.meal.data.internal.MealCollections;
import com.autosecretary.features.meal.data.internal.mapper.WeeklyFoodTargetRowMapper;
import com.autosecretary.features.meal.data.internal.storage.MealStorage;
import com.autosecretary.features.meal.domain.WeeklyFoodTarget;

import java.util.List;
import java.util.Map;

public class WeeklyFoodTargetDao extends BaseCollectionDao<WeeklyFoodTarget> {

    public WeeklyFoodTargetDao(MealStorage storage) {
        super(MealCollections.WEEKLY_FOOD_TARGETS, storage, new WeeklyFoodTargetRowMapper(), target -> target.id, (target, id) -> target.id = id);
    }

    public WeeklyFoodTarget findByPeriodKey(String periodKey) {
        List<Map<String, Object>> rows = storage.findByField(MealCollections.WEEKLY_FOOD_TARGETS, "periodKey", periodKey);
        if (rows.isEmpty()) {
            return null;
        }
        return mapper.fromRow(rows.get(0));
    }
}
