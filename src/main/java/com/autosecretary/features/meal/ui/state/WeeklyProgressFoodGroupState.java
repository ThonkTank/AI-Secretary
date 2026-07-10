package com.autosecretary.features.meal.ui.state;

import com.autosecretary.features.meal.domain.Ingredient;
import com.autosecretary.shared.ui.UiStateCarrier;

public record WeeklyProgressFoodGroupState(
        Ingredient.FoodGroup foodGroup,
        int completionPercent) implements UiStateCarrier {
}
