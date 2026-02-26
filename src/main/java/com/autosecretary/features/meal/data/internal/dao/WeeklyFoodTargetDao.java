package com.autosecretary.features.meal.data.internal.dao;

import com.autosecretary.features.meal.data.internal.MealCollections;
import com.autosecretary.features.meal.data.internal.mapper.WeeklyFoodTargetRowMapper;
import com.autosecretary.features.meal.data.internal.storage.MealStorage;
import com.autosecretary.features.meal.domain.WeeklyFoodTarget;

import java.util.List;

public class WeeklyFoodTargetDao extends BaseCollectionDao<WeeklyFoodTarget> {

    private final MealStorage storage;

    public WeeklyFoodTargetDao(MealStorage storage) {
        super(MealCollections.WEEKLY_FOOD_TARGETS, storage, new WeeklyFoodTargetRowMapper(), target -> target.id);
        this.storage = storage;
    }

    public WeeklyFoodTarget findByPeriodKey(String periodKey) {
        List<java.util.Map<String, Object>> rows = storage.findByField(MealCollections.WEEKLY_FOOD_TARGETS, "periodKey", periodKey);
        if (rows.isEmpty()) {
            return null;
        }
        return new WeeklyFoodTargetRowMapper().fromRow(rows.get(0));
    }
}
