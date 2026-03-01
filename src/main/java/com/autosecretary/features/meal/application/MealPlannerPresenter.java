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

/**
 * Application-layer coordinator for the meal-planner screen.
 *
 * <p>Called directly by {@code MealPlannerFragment}. Provides pre-sorted, ready-to-render data
 * and handles basic user actions (plan a recipe, toggle a meal done, add pantry/shopping items).
 *
 * <p>This class contains no persistence logic: all reads/writes delegate to repositories.
 * It is not a domain service — it exists solely to keep the fragment thin and testable.
 */
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

    /**
     * Returns meal plans for a rolling window centred on today: 3 days back + today + 10 days ahead.
     * The asymmetric window is intentional — showing recent history alongside upcoming plans.
     * Results are sorted by date, then by MealType ordinal (BREAKFAST before LUNCH before DINNER).
     */
    public List<MealPlan> getWeekMealPlans() {
        LocalDate today = LocalDate.now();
        // Window: 3 past days + today + 10 future days, so the planner always shows the current
        // week plus the next few days without the user having to scroll forward.
        List<MealPlan> items = mealRepository.getMealPlans(today.minusDays(3), today.plusDays(10));
        items.sort(Comparator.comparing((MealPlan plan) -> plan.date)
                .thenComparing(plan -> plan.mealType));
        return items;
    }

    /**
     * Returns all recipes sorted by title.
     *
     * <p><strong>Side effect on first use:</strong> If no recipes exist yet, a demo recipe is
     * inserted into the repository and returned. This gives a fresh install something to show and
     * confirms the persistence layer is working. The demo is a real saved record, not a placeholder.
     */
    public List<Recipe> getRecipes() {
        List<Recipe> recipes = new ArrayList<>(recipeRepository.getRecipes());
        if (recipes.isEmpty()) {
            // Seed a demo recipe so the screen is not blank on first launch.
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

    public List<PantryItem> getPantryItems() {
        List<PantryItem> items = pantryRepository.getPantryItems();
        items.sort(Comparator.comparing(item -> item.ingredientName));
        return items;
    }

    /**
     * Returns shopping-list items for the current period.
     * Period key is an ISO-8601 date string ({@code LocalDate.toString()}, e.g. {@code "2024-12-30"}).
     * Items from different days within the same shopping trip share the same key.
     */
    public List<ShoppingListItem> getShoppingListItemsForCurrentPeriod() {
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
        MealPlan plan = mealRepository.findMealPlanById(mealPlanId);
        if (plan == null) {
            return;
        }
        plan.isCompleted = !plan.isCompleted;
        plan.completedAt = plan.isCompleted ? LocalDateTime.now() : null;
        if (plan.isCompleted) {
            plan.actualServings = Math.max(1, plan.plannedServings);
        }
        mealRepository.saveMealPlan(plan);
    }

    public void createShoppingItemFromNeed(String ingredientName, double neededAmount, String unit) {
        // ingredientId = -1: this item is created from a free-text "need" entry, not linked to a
        // specific Ingredient entity. No ingredient lookup or package-size rounding is applied.
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
