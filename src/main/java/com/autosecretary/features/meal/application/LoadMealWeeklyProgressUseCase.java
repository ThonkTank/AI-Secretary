package com.autosecretary.features.meal.application;

import com.autosecretary.features.meal.domain.ConsumptionLog;
import com.autosecretary.features.meal.domain.HouseholdMember;
import com.autosecretary.features.meal.domain.Ingredient;
import com.autosecretary.features.meal.domain.MealRepository;
import com.autosecretary.features.meal.domain.WeeklyFoodTarget;
import com.autosecretary.features.meal.domain.WeeklyFoodTargetService;
import com.autosecretary.features.meal.domain.internal.HouseholdEnergyService;

import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class LoadMealWeeklyProgressUseCase {
    private final MealRepository mealRepository;

    public LoadMealWeeklyProgressUseCase(MealRepository mealRepository) {
        this.mealRepository = mealRepository;
    }

    public WeeklyProgressOverview load(LocalDate today) {
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        LocalDate weekEnd = weekStart.plusDays(6);
        String periodKey = weekStart.toString();

        WeeklyFoodTarget weeklyTarget = mealRepository.findWeeklyFoodTarget(periodKey);
        List<HouseholdMember> members = mealRepository.getHouseholdMembers();
        if (weeklyTarget == null) {
            weeklyTarget = WeeklyFoodTargetService.calculate(periodKey, members, today);
            mealRepository.saveWeeklyFoodTarget(weeklyTarget);
        }

        int calorieTarget = members.stream()
                .filter(member -> member != null && member.isActive)
                .mapToInt(member -> HouseholdEnergyService.calculateTdee(member, today))
                .sum() * 7;

        List<ConsumptionLog> consumptionLogs = mealRepository.getConsumptionLogs(weekStart, weekEnd);
        int calorieActual = consumptionLogs.stream().mapToInt(log -> Math.max(0, log.calories)).sum();
        int calorieCompletionPercent = toPercent(calorieActual, calorieTarget);

        List<WeeklyProgressFoodGroup> groups = new ArrayList<>();
        for (Ingredient.FoodGroup foodGroup : Ingredient.FoodGroup.values()) {
            if (foodGroup == Ingredient.FoodGroup.OTHER) continue;
            int targetGrams = Math.max(0, weeklyTarget.getTargetFor(foodGroup));
            int actualGrams = estimateActualFromCalories(targetGrams, calorieActual, calorieTarget);
            int remainingGrams = Math.max(0, targetGrams - actualGrams);
            int completionPercent = toPercent(actualGrams, targetGrams);
            groups.add(new WeeklyProgressFoodGroup(foodGroup, targetGrams, actualGrams, remainingGrams,
                    completionPercent));
        }
        groups.sort(Comparator.comparing(group -> group.foodGroup.ordinal()));

        return new WeeklyProgressOverview(
                weekStart,
                weekEnd,
                calorieTarget,
                calorieActual,
                Math.max(0, calorieTarget - calorieActual),
                calorieCompletionPercent,
                groups
        );
    }

    private int estimateActualFromCalories(int target, int actualCalories, int targetCalories) {
        if (target <= 0 || actualCalories <= 0 || targetCalories <= 0) return 0;
        return (int) Math.round((target * (double) actualCalories) / targetCalories);
    }

    private int toPercent(int actual, int target) {
        if (target <= 0) return 0;
        return (int) Math.round((actual * 100.0) / target);
    }
}
