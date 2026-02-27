package com.autosecretary.features.meal.application;

import com.autosecretary.features.meal.domain.MealPlan;
import com.autosecretary.features.meal.domain.MealRepository;
import com.autosecretary.features.meal.domain.MealType;
import com.autosecretary.features.meal.domain.PantryItem;
import com.autosecretary.features.meal.domain.PantryRepository;
import com.autosecretary.features.meal.domain.Recipe;
import com.autosecretary.features.meal.domain.RecipeRepository;
import com.autosecretary.features.meal.domain.ShoppingListItem;
import com.autosecretary.features.meal.domain.ShelfLifeService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class MealPlannerPresenter {

    private final MealRepository mealRepository;
    private final RecipeRepository recipeRepository;
    private final PantryRepository pantryRepository;

    public MealPlannerPresenter(MealRepository mealRepository,
                                RecipeRepository recipeRepository,
                                PantryRepository pantryRepository) {
        this.mealRepository = mealRepository;
        this.recipeRepository = recipeRepository;
        this.pantryRepository = pantryRepository;
    }

    public List<MealPlan> getWeekMealPlans() {
        LocalDate today = LocalDate.now();
        List<MealPlan> items = mealRepository.getMealPlans(today.minusDays(3), today.plusDays(10));
        items.sort(Comparator.comparing((MealPlan plan) -> plan.date)
                .thenComparing(plan -> plan.mealType.ordinal()));
        return items;
    }

    public List<Recipe> getRecipes() {
        List<Recipe> recipes = new ArrayList<>(recipeRepository.getRecipes());
        recipes.sort(Comparator.comparing(recipe -> recipe.title));
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
        return recipes;
    }

    public List<PantryItem> getPantryItems() {
        List<PantryItem> items = pantryRepository.getPantryItems();
        items.sort(Comparator.comparing(item -> item.ingredientName));
        return items;
    }

    public List<ShoppingListItem> getShoppingListItemsForToday() {
        return pantryRepository.getShoppingListItems(LocalDate.now().toString());
    }

    public void planRecipe(long recipeId, LocalDate date, MealType mealType, int servings) {
        Recipe recipe = recipeRepository.findRecipeById(recipeId);
        if (recipe == null) {
            return;
        }
        MealPlan plan = new MealPlan.Builder(date, mealType, recipeId)
                .servings(Math.max(1, servings))
                .recipeTitle(recipe.title)
                .calories(recipe.totalCalories)
                .build();
        mealRepository.saveMealPlan(plan);
    }

    public void toggleMealCompleted(long mealPlanId) {
        for (MealPlan plan : getWeekMealPlans()) {
            if (plan.id != null && plan.id == mealPlanId) {
                plan.isCompleted = !plan.isCompleted;
                plan.completedAt = plan.isCompleted ? LocalDateTime.now() : null;
                if (plan.isCompleted) {
                    plan.actualServings = Math.max(1, plan.plannedServings);
                }
                mealRepository.saveMealPlan(plan);
                return;
            }
        }
    }

    public void createShoppingItemFromNeed(String ingredientName, double neededAmount, String unit) {
        ShoppingListItem item = new ShoppingListItem.Builder(-1, ingredientName, Math.max(0.1, neededAmount), unit)
                .periodKey(LocalDate.now().toString())
                .build();
        pantryRepository.saveShoppingListItem(item);
    }

    public void createPantryItem(String ingredientName,
                                 double amount,
                                 String unit,
                                 PantryItem.StorageLocation location,
                                 int shelfLifeDays) {
        LocalDate purchaseDate = LocalDate.now();
        LocalDate expiryDate = ShelfLifeService.calculateExpiryDate(purchaseDate, shelfLifeDays);
        PantryItem item = new PantryItem.Builder(-1, ingredientName, Math.max(0.1, amount), unit)
                .purchaseDate(purchaseDate)
                .expiryDate(expiryDate)
                .location(location)
                .build();
        pantryRepository.savePantryItem(item);
    }
}
