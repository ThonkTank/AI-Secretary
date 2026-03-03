package com.autosecretary.features.meal.application;

import com.autosecretary.features.meal.domain.MealPlan;
import com.autosecretary.features.meal.domain.MealRepository;
import com.autosecretary.features.meal.domain.MealType;
import com.autosecretary.features.meal.domain.HouseholdMember;
import com.autosecretary.features.meal.domain.Ingredient;
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
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Application-layer coordinator for the meal-planner screen.
 *
 * <p>Called directly by {@code MealPlannerFragment}. Provides pre-sorted, ready-to-render data
 * and handles basic user actions (plan a recipe, toggle a meal done, add pantry/shopping items).
 *
 * <p><strong>Threading contract:</strong> All repository work is executed on the provided worker
 * {@link Executor}. Every completion callback is dispatched via {@code callbackDispatcher}
 * (typically main/UI thread). Callers can safely update UI in callbacks.
 *
 * <p>This class contains no persistence logic: all reads/writes delegate to repositories.
 * It is not a domain service — it exists solely to keep the fragment thin.
 */
public class MealPlannerPresenter {

    private final MealRepository mealRepository;
    private final RecipeRepository recipeRepository;
    private final PantryRepository pantryRepository;
    private final Executor workerExecutor;
    private final Executor callbackDispatcher;

    public MealPlannerPresenter(MealRepository mealRepository,
                                RecipeRepository recipeRepository,
                                PantryRepository pantryRepository,
                                Executor workerExecutor,
                                Executor callbackDispatcher) {
        this.mealRepository = mealRepository;
        this.recipeRepository = recipeRepository;
        this.pantryRepository = pantryRepository;
        this.workerExecutor = workerExecutor;
        this.callbackDispatcher = callbackDispatcher;
    }

    /**
     * Loads meal plans for a rolling window centred on today: 3 days back + today + 10 days ahead.
     * The asymmetric window is intentional — showing recent history alongside upcoming plans.
     * Results are sorted by date, then by MealType ordinal (BREAKFAST before LUNCH before DINNER).
     *
     * @param onLoaded receives the sorted list on the callback dispatcher thread
     */
    public void getWeekMealPlans(Consumer<List<MealPlan>> onLoaded) {
        workerExecutor.execute(() -> {
            LocalDate today = LocalDate.now();
            List<MealPlan> items = mealRepository.getMealPlans(today.minusDays(3), today.plusDays(10));
            items.sort(Comparator.comparing((MealPlan plan) -> plan.date)
                    .thenComparing(plan -> plan.mealType));
            callbackDispatcher.execute(() -> onLoaded.accept(items));
        });
    }

    /**
     * Loads all recipes sorted by title.
     *
     * <p><strong>Side effect on first use:</strong> If no recipes exist yet, a demo recipe is
     * inserted into the repository and returned. This gives a fresh install something to show and
     * confirms the persistence layer is working. The demo is a real saved record, not a placeholder.
     *
     * @param onLoaded receives the sorted list on the callback dispatcher thread
     */
    public void getRecipes(Consumer<List<Recipe>> onLoaded) {
        workerExecutor.execute(() -> {
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
            callbackDispatcher.execute(() -> onLoaded.accept(recipes));
        });
    }

    public void getIngredients(Consumer<List<Ingredient>> onLoaded) {
        workerExecutor.execute(() -> {
            List<Ingredient> items = recipeRepository.getIngredients();
            items.sort(Comparator.comparing(i -> i.name));
            callbackDispatcher.execute(() -> onLoaded.accept(items));
        });
    }

    public void getHouseholdMembers(Consumer<List<HouseholdMember>> onLoaded) {
        workerExecutor.execute(() -> {
            List<HouseholdMember> items = mealRepository.getHouseholdMembers();
            items.sort(Comparator.comparing(i -> i.name));
            callbackDispatcher.execute(() -> onLoaded.accept(items));
        });
    }

    /**
     * Loads all pantry items sorted by ingredient name.
     *
     * @param onLoaded receives the sorted list on the callback dispatcher thread
     */
    public void getPantryItems(Consumer<List<PantryItem>> onLoaded) {
        workerExecutor.execute(() -> {
            List<PantryItem> items = pantryRepository.getPantryItems();
            items.sort(Comparator.comparing(item -> item.ingredientName));
            callbackDispatcher.execute(() -> onLoaded.accept(items));
        });
    }

    /**
     * Loads shopping-list items for the current period.
     * Period key is an ISO-8601 date string ({@code LocalDate.toString()}, e.g. {@code "2024-12-30"}).
     *
     * @param onLoaded receives the list on the callback dispatcher thread
     */
    public void getShoppingListItems(Consumer<List<ShoppingListItem>> onLoaded) {
        workerExecutor.execute(() -> {
            List<ShoppingListItem> items =
                    pantryRepository.getShoppingListItems(LocalDate.now().toString());
            callbackDispatcher.execute(() -> onLoaded.accept(items));
        });
    }

    /**
     * Plans a recipe for the given date and meal type.
     *
     * @param onDone called on the callback dispatcher thread after the plan is saved,
     *               or immediately if the recipe was not found
     */
    public void planRecipe(String recipeId, LocalDate date, MealType mealType, int servings,
                           Runnable onDone) {
        workerExecutor.execute(() -> {
            Recipe recipe = recipeRepository.findRecipeById(recipeId);
            if (recipe != null) {
                MealPlan plan = new MealPlan.Builder(date, mealType, recipeId)
                        .servings(Math.max(1, servings))
                        .recipeTitle(recipe.title)
                        .calories(recipe.totalCalories)
                        .build();
                mealRepository.saveMealPlan(plan);
            }
            callbackDispatcher.execute(onDone);
        });
    }

    /**
     * Toggles the completion state of a meal plan.
     *
     * @param onDone called on the callback dispatcher thread after the toggle is persisted
     */
    public void toggleMealCompleted(String mealPlanId, Runnable onDone) {
        workerExecutor.execute(() -> {
            MealPlan plan = mealRepository.findMealPlanById(mealPlanId);
            if (plan != null) {
                plan.isCompleted = !plan.isCompleted;
                plan.completedAt = plan.isCompleted ? LocalDateTime.now() : null;
                if (plan.isCompleted) {
                    plan.actualServings = Math.max(1, plan.plannedServings);
                }
                mealRepository.saveMealPlan(plan);
            }
            callbackDispatcher.execute(onDone);
        });
    }

    /**
     * Creates a shopping list item from a free-text need entry.
     *
     * @param onDone called on the callback dispatcher thread after the item is saved
     */
    public void createShoppingItemFromNeed(String ingredientName, double neededAmount, String unit,
                                           Runnable onDone) {
        workerExecutor.execute(() -> {
            ShoppingListItem item = new ShoppingListItem.Builder(null, ingredientName, Math.max(0.1, neededAmount), unit)
                    .periodKey(LocalDate.now().toString())
                    .build();
            pantryRepository.saveShoppingListItem(item);
            callbackDispatcher.execute(onDone);
        });
    }

    /**
     * Creates a pantry item with the given properties.
     *
     * @param onDone called on the callback dispatcher thread after the item is saved
     */
    public void createPantryItem(String ingredientName,
                                 double amount,
                                 String unit,
                                 PantryItem.StorageLocation location,
                                 int shelfLifeDays,
                                 Runnable onDone) {
        workerExecutor.execute(() -> {
            LocalDate purchaseDate = LocalDate.now();
            LocalDate expiryDate = ShelfLifeService.calculateExpiryDate(purchaseDate, shelfLifeDays);
            PantryItem item = new PantryItem.Builder(null, ingredientName, Math.max(0.1, amount), unit)
                    .purchaseDate(purchaseDate)
                    .expiryDate(expiryDate)
                    .location(location)
                    .build();
            pantryRepository.savePantryItem(item);
            callbackDispatcher.execute(onDone);
        });
    }

    public void createRecipe(String title,
                             String description,
                             String instructions,
                             int servings,
                             Runnable onDone) {
        workerExecutor.execute(() -> {
            Recipe recipe = new Recipe.Builder(title)
                    .description(description)
                    .instructions(instructions)
                    .mealType(MealType.DINNER)
                    .servings(Math.max(1, servings))
                    .build();
            recipeRepository.saveRecipe(recipe);
            callbackDispatcher.execute(onDone);
        });
    }

    public void createIngredient(String name,
                                 Ingredient.FoodGroup group,
                                 String unit,
                                 Runnable onDone) {
        workerExecutor.execute(() -> {
            Ingredient ingredient = new Ingredient.Builder(name, group)
                    .unit(unit, 1)
                    .build();
            recipeRepository.saveIngredient(ingredient);
            callbackDispatcher.execute(onDone);
        });
    }

    public void createHouseholdMember(String name,
                                      HouseholdMember.Gender gender,
                                      int birthYear,
                                      Runnable onDone) {
        workerExecutor.execute(() -> {
            HouseholdMember member = new HouseholdMember.Builder(name, gender)
                    .birthYear(birthYear)
                    .build();
            mealRepository.saveHouseholdMember(member);
            callbackDispatcher.execute(onDone);
        });
    }
}
