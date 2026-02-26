package com.autosecretary.features.meal.data.internal.dao;

import com.autosecretary.features.meal.data.internal.MealCollections;
import com.autosecretary.features.meal.data.internal.mapper.MealPlanRowMapper;
import com.autosecretary.features.meal.data.internal.storage.MealStorage;
import com.autosecretary.features.meal.domain.MealPlan;

import java.time.LocalDate;
import java.util.List;

public class MealPlanDao extends BaseCollectionDao<MealPlan> {

    public MealPlanDao(MealStorage storage) {
        super(MealCollections.MEAL_PLANS, storage, new MealPlanRowMapper(), mealPlan -> mealPlan.id, (mealPlan, id) -> mealPlan.id = id);
    }

    public List<MealPlan> findInRange(LocalDate fromInclusive, LocalDate toInclusive) {
        return findAll(plan -> plan.date != null && !plan.date.isBefore(fromInclusive) && !plan.date.isAfter(toInclusive));
    }
}
