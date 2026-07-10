package com.autosecretary.features.meal.ui.state;

import com.autosecretary.features.meal.domain.MealPlan;
import com.autosecretary.features.meal.domain.ShoppingListItem;
import com.autosecretary.shared.ui.UiStateCarrier;

import java.util.List;

public record HomeState(
        List<MealPlan> weekPlans,
        List<ShoppingListItem> shoppingItems) implements UiStateCarrier {
}
