package com.autosecretary.features.meal.application;

import com.autosecretary.features.meal.domain.MealPlan;
import com.autosecretary.features.meal.domain.MealRepository;
import com.autosecretary.features.meal.domain.MealType;
import com.autosecretary.features.meal.domain.PantryItem;
import com.autosecretary.features.meal.domain.PantryRepository;
import com.autosecretary.features.meal.domain.Recipe;
import com.autosecretary.features.meal.domain.RecipeRepository;
import com.autosecretary.features.meal.domain.ShoppingItemStatus;
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

    public void loadHome(Consumer<MealHomeModel> onLoaded) {
        workerExecutor.execute(() -> {
            LocalDate today = LocalDate.now();
            List<MealPlan> plans = loadSortedMealPlans(today);
            List<Recipe> recipes = loadSortedRecipes();
            List<PantryItem> pantryItems = loadSortedPantryItems();
            List<ShoppingListItem> shoppingItems = loadShoppingList(today);

            MealHomeModel homeModel = new MealHomeModel(
                    plans,
                    recipes,
                    pantryItems,
                    shoppingItems,
                    createWeekPlanSnapshot(plans),
                    createNeedProgress(shoppingItems),
                    createShoppingFocus(shoppingItems)
            );
            callbackDispatcher.execute(() -> onLoaded.accept(homeModel));
        });
    }

    public void openManagePlan(Consumer<List<Recipe>> onReady) {
        workerExecutor.execute(() -> {
            List<Recipe> recipes = loadSortedRecipes();
            callbackDispatcher.execute(() -> onReady.accept(recipes));
        });
    }

    public void openManageNeed(Runnable onReady) {
        callbackDispatcher.execute(onReady);
    }

    public void openManagePantry(Runnable onReady) {
        callbackDispatcher.execute(onReady);
    }

    public void updateShoppingItemStatus(String shoppingItemId, ShoppingItemStatus status, Runnable onDone) {
        workerExecutor.execute(() -> {
            pantryRepository.updateShoppingItemStatus(shoppingItemId, status);
            callbackDispatcher.execute(onDone);
        });
    }


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

    private List<MealPlan> loadSortedMealPlans(LocalDate today) {
        List<MealPlan> plans = mealRepository.getMealPlans(today.minusDays(3), today.plusDays(10));
        plans.sort(Comparator.comparing((MealPlan plan) -> plan.date)
                .thenComparing(plan -> plan.mealType));
        return plans;
    }

    private List<Recipe> loadSortedRecipes() {
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

    private List<PantryItem> loadSortedPantryItems() {
        List<PantryItem> items = pantryRepository.getPantryItems();
        items.sort(Comparator.comparing(item -> item.ingredientName));
        return items;
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
