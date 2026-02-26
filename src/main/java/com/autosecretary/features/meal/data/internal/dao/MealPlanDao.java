package com.autosecretary.features.meal.data.internal.dao;

import com.autosecretary.features.meal.data.internal.MealCollections;
import com.autosecretary.features.meal.data.internal.mapper.MealPlanRowMapper;
import com.autosecretary.features.meal.data.internal.storage.MealStorage;
import com.autosecretary.features.meal.domain.MealPlan;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MealPlanDao extends BaseCollectionDao<MealPlan> {
    private final MealStorage storage;

    public MealPlanDao(MealStorage storage) {
        super(MealCollections.MEAL_PLANS, storage, new MealPlanRowMapper(), mealPlan -> mealPlan.id);
        this.storage = storage;
    }

    public List<MealPlan> findInRange(LocalDate fromInclusive, LocalDate toInclusive) {
        List<MealPlan> plans = new ArrayList<>();
        MealPlanRowMapper mapper = new MealPlanRowMapper();
        for (Map<String, Object> row : storage.findAll(MealCollections.MEAL_PLANS)) {
            MealPlan plan = mapper.fromRow(row);
            if (plan.date != null && !plan.date.isBefore(fromInclusive) && !plan.date.isAfter(toInclusive)) {
                plans.add(plan);
            }
        }
        return plans;
    }
}
