package com.autosecretary.features.meal.application;

import com.autosecretary.features.meal.domain.HouseholdMember;
import com.autosecretary.features.meal.domain.MealRepository;
import com.autosecretary.features.meal.domain.WeeklyFoodTarget;
import com.autosecretary.features.meal.domain.WeeklyFoodTargetService;

import java.time.LocalDate;
import java.util.List;

/**
 * Application-Use-Case zum Neuberechnen der DGE-Wochenziele fuer einen Zeitraum.
 */
public class RecalculateWeeklyFoodTargetUseCase {

    private final MealRepository mealRepository;

    public RecalculateWeeklyFoodTargetUseCase(MealRepository mealRepository) {
        this.mealRepository = mealRepository;
    }

    public WeeklyFoodTarget execute(String periodKey, LocalDate referenceDate) {
        List<HouseholdMember> members = mealRepository.getHouseholdMembers();
        WeeklyFoodTarget target = WeeklyFoodTargetService.calculate(periodKey, members, referenceDate);
        mealRepository.saveWeeklyFoodTarget(target);
        return target;
    }
}
