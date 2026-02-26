package com.autosecretary.features.meal.domain;

import java.time.LocalDate;
import java.util.List;

public interface MealRepository {
    List<MealPlan> getMealPlans(LocalDate fromInclusive, LocalDate toInclusive);
    void saveMealPlan(MealPlan mealPlan);
    void deleteMealPlan(long mealPlanId);

    List<ConsumptionLog> getConsumptionLogs(LocalDate fromInclusive, LocalDate toInclusive);
    void saveConsumptionLog(ConsumptionLog consumptionLog);

    List<HouseholdMember> getHouseholdMembers();
    void saveHouseholdMember(HouseholdMember member);
    void deleteHouseholdMember(long memberId);

    CookingPreferences getCookingPreferences();
    void saveCookingPreferences(CookingPreferences preferences);

    WeeklyFoodTarget findWeeklyFoodTarget(String periodKey);
    void saveWeeklyFoodTarget(WeeklyFoodTarget weeklyFoodTarget);
}
