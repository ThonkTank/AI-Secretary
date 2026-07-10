package com.autosecretary.features.meal.application;

import com.autosecretary.features.meal.domain.MealPlan;
import com.autosecretary.features.meal.domain.MealRepository;
import com.autosecretary.shared.MealType;
import com.autosecretary.features.meal.domain.PantryItem;
import com.autosecretary.features.meal.domain.PantryRepository;
import com.autosecretary.features.meal.domain.Recipe;
import com.autosecretary.features.meal.domain.RecipeRepository;
import com.autosecretary.features.meal.domain.ShoppingListItem;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class LoadMealHomeUseCase {
    private final MealRepository mealRepository;
    private final RecipeRepository recipeRepository;
    private final PantryRepository pantryRepository;

    public LoadMealHomeUseCase(MealRepository mealRepository,
                               RecipeRepository recipeRepository,
                               PantryRepository pantryRepository) {
        this.mealRepository = mealRepository;
        this.recipeRepository = recipeRepository;
        this.pantryRepository = pantryRepository;
    }

    public MealHomeModel load(LocalDate today) {
        List<MealPlan> plans = loadSortedMealPlans(today);
        List<Recipe> recipes = loadSortedRecipes();
        List<PantryItem> pantryItems = loadSortedPantryItems();
        List<ShoppingListItem> shoppingItems = loadShoppingList(today);

        return new MealHomeModel(
                plans,
                recipes,
                pantryItems,
                shoppingItems,
                createWeekPlanSnapshot(plans),
                createNeedProgress(shoppingItems),
                createShoppingFocus(shoppingItems)
        );
    }

    List<Recipe> loadSortedRecipes() {
        List<Recipe> recipes = new ArrayList<>(recipeRepository.getRecipes());
        if (recipes.isEmpty()) {
            Recipe demo = new Recipe.Builder("Pasta Primavera")
                    .description("Schnelle Gemüse-Pasta")
                    .instructions("Pasta kochen, Gemüse anbraten, mischen.")
                    .mealType(MealType.DINNER)
                    .servings(2)
                    .build();
            recipeRepository.saveRecipe(demo);
            recipes.add(demo);
        }
        recipes.sort(Comparator.comparing(recipe -> recipe.title));
        return recipes;
    }

    List<PantryItem> loadSortedPantryItems() {
        List<PantryItem> items = pantryRepository.getPantryItems();
        items.sort(Comparator.comparing(item -> item.ingredientName));
        return items;
    }

    private List<MealPlan> loadSortedMealPlans(LocalDate today) {
        List<MealPlan> plans = mealRepository.getMealPlans(today.minusDays(3), today.plusDays(10));
        plans.sort(Comparator.comparing((MealPlan plan) -> plan.date)
                .thenComparing(plan -> plan.mealType));
        return plans;
    }

    private List<ShoppingListItem> loadShoppingList(LocalDate day) {
        List<ShoppingListItem> items = pantryRepository.getShoppingListItems(day.toString());
        items.sort(Comparator.comparing((ShoppingListItem item) -> item.status)
                .thenComparing(item -> item.ingredientName));
        return items;
    }

    private MealHomeModel.WeekPlanSnapshot createWeekPlanSnapshot(List<MealPlan> plans) {
        int completedCount = 0;
        for (MealPlan plan : plans) {
            if (plan.isCompleted) {
                completedCount++;
            }
        }
        return new MealHomeModel.WeekPlanSnapshot(plans.size(), completedCount);
    }

    private MealHomeModel.NeedProgress createNeedProgress(List<ShoppingListItem> shoppingItems) {
        double totalNeeded = 0.0;
        double purchasedAmount = 0.0;
        for (ShoppingListItem item : shoppingItems) {
            double neededAmount = Math.max(0.0, item.neededAmount);
            totalNeeded += neededAmount;
            if (item.isDone()) {
                purchasedAmount += neededAmount;
            }
        }
        double completionRatio = totalNeeded <= 0.0 ? 0.0 : Math.min(1.0, purchasedAmount / totalNeeded);
        return new MealHomeModel.NeedProgress(totalNeeded, purchasedAmount, completionRatio);
    }

    private MealHomeModel.ShoppingFocus createShoppingFocus(List<ShoppingListItem> shoppingItems) {
        int openItems = 0;
        ShoppingListItem topItem = null;
        for (ShoppingListItem item : shoppingItems) {
            if (!item.isDone()) {
                openItems++;
                if (topItem == null || item.neededAmount > topItem.neededAmount) {
                    topItem = item;
                }
            }
        }
        String topIngredient = topItem != null ? topItem.ingredientName : "";
        return new MealHomeModel.ShoppingFocus(openItems, topIngredient);
    }
}
