package com.autosecretary.features.meal.application.usecase;

import com.autosecretary.features.meal.domain.HouseholdMember;
import com.autosecretary.features.meal.domain.MealRepository;
import com.autosecretary.features.meal.domain.WeeklyFoodTarget;
import com.autosecretary.features.meal.domain.internal.HouseholdEnergyService;
import com.autosecretary.features.meal.domain.internal.WeeklyFoodTargetService;

import java.time.LocalDate;
import java.util.List;

/**
 * Application-Use-Case zum Neuberechnen der DGE-Wochenziele fuer einen Zeitraum.
 */
public class RecalculateWeeklyFoodTargetUseCase {

    private final MealRepository mealRepository;
    private final WeeklyFoodTargetService targetService;

    public RecalculateWeeklyFoodTargetUseCase(MealRepository mealRepository) {
        this(mealRepository, new WeeklyFoodTargetService(new HouseholdEnergyService()));
    }

    public RecalculateWeeklyFoodTargetUseCase(MealRepository mealRepository,
                                              WeeklyFoodTargetService targetService) {
        this.mealRepository = mealRepository;
        this.targetService = targetService;
    }

    public WeeklyFoodTarget execute(String periodKey, LocalDate referenceDate) {
        List<HouseholdMember> members = mealRepository.getHouseholdMembers();
        WeeklyFoodTarget target = targetService.calculate(periodKey, members, referenceDate);
        mealRepository.saveWeeklyFoodTarget(target);
        return target;
    }
}
