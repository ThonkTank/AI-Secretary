package com.autosecretary.features.meal.application;

import com.autosecretary.features.meal.domain.HouseholdMember;
import com.autosecretary.features.meal.domain.MealRepository;
import com.autosecretary.features.meal.domain.WeeklyFoodTarget;
import com.autosecretary.features.meal.domain.WeeklyFoodTargetService;

import java.time.LocalDate;
import java.util.List;

/**
 * Re-derives the weekly nutrition target for all current household members and persists it.
 *
 * <p>Targets are based on DGE (Deutsche Gesellschaft für Ernährung — German Nutrition Society)
 * reference values for weekly food-group intake, adjusted per member for age, gender, and
 * activity level. The calculation is delegated to
 * {@link com.autosecretary.features.meal.domain.WeeklyFoodTargetService}.
 *
 * <p><strong>Parameters:</strong>
 * <ul>
 *   <li>{@code periodKey} — ISO-8601 date string identifying the week (e.g. {@code "2024-12-30"}).
 *       Use {@code LocalDate.now().toString()} for the current week.</li>
 *   <li>{@code referenceDate} — The date used to compute member ages during calculation.
 *       Pass {@code LocalDate.now()} for current values.</li>
 * </ul>
 *
 * <p>Call this whenever household members are added/modified, or at the start of each new week.
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
