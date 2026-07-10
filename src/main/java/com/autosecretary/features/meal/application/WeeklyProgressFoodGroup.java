package com.autosecretary.features.meal.application;

import com.autosecretary.features.meal.domain.Ingredient;

public final class WeeklyProgressFoodGroup {
    public final Ingredient.FoodGroup foodGroup;
    public final int targetGrams;
    public final int actualGrams;
    public final int remainingGrams;
    public final int completionPercent;

    WeeklyProgressFoodGroup(Ingredient.FoodGroup foodGroup,
                            int targetGrams,
                            int actualGrams,
                            int remainingGrams,
                            int completionPercent) {
        this.foodGroup = foodGroup;
        this.targetGrams = targetGrams;
        this.actualGrams = actualGrams;
        this.remainingGrams = remainingGrams;
        this.completionPercent = completionPercent;
    }
}
