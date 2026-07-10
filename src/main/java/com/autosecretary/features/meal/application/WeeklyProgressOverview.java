package com.autosecretary.features.meal.application;

import java.time.LocalDate;
import java.util.List;

public final class WeeklyProgressOverview {
    public final LocalDate fromDate;
    public final LocalDate toDate;
    public final int calorieTarget;
    public final int calorieActual;
    public final int calorieRemaining;
    public final int calorieCompletionPercent;
    public final List<WeeklyProgressFoodGroup> foodGroups;

    WeeklyProgressOverview(LocalDate fromDate,
                           LocalDate toDate,
                           int calorieTarget,
                           int calorieActual,
                           int calorieRemaining,
                           int calorieCompletionPercent,
                           List<WeeklyProgressFoodGroup> foodGroups) {
        this.fromDate = fromDate;
        this.toDate = toDate;
        this.calorieTarget = calorieTarget;
        this.calorieActual = calorieActual;
        this.calorieRemaining = calorieRemaining;
        this.calorieCompletionPercent = calorieCompletionPercent;
        this.foodGroups = foodGroups;
    }
}
