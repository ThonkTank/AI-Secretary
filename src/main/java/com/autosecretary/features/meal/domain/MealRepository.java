package com.autosecretary.features.meal.domain;

import java.time.LocalDate;
import java.util.List;

/**
 * Domain repository for meal plans, consumption logs, household members, cooking preferences,
 * and weekly food targets.
 *
 * <p><strong>Thread safety:</strong> All implementations run on the app's single shared
 * {@code ExecutorService} (wired in {@code AppCompositionRoot}). Do not call these methods
 * on the Android main thread.</p>
 *
 * <p><strong>Return value conventions:</strong> List-returning methods return an empty list
 * (never null) when no data is found. {@link #findMealPlanById} and {@link #findWeeklyFoodTarget}
 * return null when no matching record exists. {@link #getCookingPreferences()} returns a default
 * instance with sensible defaults if none has been saved yet.</p>
 *
 * <p><strong>periodKey</strong> is a date string (ISO format, e.g. {@code "2026-02-14"})
 * used as a storage key for period-specific records such as weekly food targets.</p>
 *
 * <p>The implementation lives in {@code meal/data/internal/repository/StorageMealRepository}.</p>
 */
public interface MealRepository {
    List<MealPlan> getMealPlans(LocalDate fromInclusive, LocalDate toInclusive);
    /** Returns the meal plan with the given id, or null if not found. */
    MealPlan findMealPlanById(long mealPlanId);
    void saveMealPlan(MealPlan mealPlan);
    void deleteMealPlan(long mealPlanId);

    List<ConsumptionLog> getConsumptionLogs(LocalDate fromInclusive, LocalDate toInclusive);
    void saveConsumptionLog(ConsumptionLog consumptionLog);

    List<HouseholdMember> getHouseholdMembers();
    void saveHouseholdMember(HouseholdMember member);
    void deleteHouseholdMember(long memberId);

    CookingPreferences getCookingPreferences();
    void saveCookingPreferences(CookingPreferences preferences);

    /** Returns the target for the given periodKey, or null if none has been saved. */
    WeeklyFoodTarget findWeeklyFoodTarget(String periodKey);
    void saveWeeklyFoodTarget(WeeklyFoodTarget weeklyFoodTarget);
}
