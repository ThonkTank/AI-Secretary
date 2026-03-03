package com.autosecretary.features.meal.application;

import com.autosecretary.features.meal.domain.MealPlan;
import com.autosecretary.features.meal.domain.PantryItem;
import com.autosecretary.features.meal.domain.Recipe;
import com.autosecretary.features.meal.domain.ShoppingListItem;

import java.util.List;

/**
 * Aggregated payload for the meal home screen.
 */
public class MealHomeModel {

    public final List<MealPlan> weekPlans;
    public final List<Recipe> recipes;
    public final List<PantryItem> pantryItems;
    public final List<ShoppingListItem> shoppingItems;
    public final WeekPlanSnapshot weekPlanSnapshot;
    public final NeedProgress needProgress;
    public final ShoppingFocus shoppingFocus;

    public MealHomeModel(List<MealPlan> weekPlans,
                         List<Recipe> recipes,
                         List<PantryItem> pantryItems,
                         List<ShoppingListItem> shoppingItems,
                         WeekPlanSnapshot weekPlanSnapshot,
                         NeedProgress needProgress,
                         ShoppingFocus shoppingFocus) {
        this.weekPlans = weekPlans;
        this.recipes = recipes;
        this.pantryItems = pantryItems;
        this.shoppingItems = shoppingItems;
        this.weekPlanSnapshot = weekPlanSnapshot;
        this.needProgress = needProgress;
        this.shoppingFocus = shoppingFocus;
    }

    public static class WeekPlanSnapshot {
        public final int plannedCount;
        public final int completedCount;

        public WeekPlanSnapshot(int plannedCount, int completedCount) {
            this.plannedCount = plannedCount;
            this.completedCount = completedCount;
        }
    }

    public static class NeedProgress {
        public final double totalNeeded;
        public final double purchasedAmount;
        public final double completionRatio;

        public NeedProgress(double totalNeeded, double purchasedAmount, double completionRatio) {
            this.totalNeeded = totalNeeded;
            this.purchasedAmount = purchasedAmount;
            this.completionRatio = completionRatio;
        }
    }

    public static class ShoppingFocus {
        public final int openItems;
        public final String topIngredient;

        public ShoppingFocus(int openItems, String topIngredient) {
            this.openItems = openItems;
            this.topIngredient = topIngredient;
        }
    }
}
