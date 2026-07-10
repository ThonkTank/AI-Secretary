package com.autosecretary.features.meal.application;

import com.autosecretary.features.meal.domain.MealPlan;
import com.autosecretary.features.meal.domain.MealRepository;
import com.autosecretary.shared.MealType;
import com.autosecretary.features.meal.domain.PantryItem;
import com.autosecretary.features.meal.domain.PantryRepository;
import com.autosecretary.features.meal.domain.Recipe;
import com.autosecretary.features.meal.domain.RecipeRepository;
import com.autosecretary.features.meal.domain.CookingPreferences;
import com.autosecretary.features.meal.domain.ConsumptionLog;
import com.autosecretary.features.meal.domain.HouseholdMember;
import com.autosecretary.features.meal.domain.Ingredient;
import com.autosecretary.features.meal.domain.ShelfLifeService;
import com.autosecretary.features.meal.domain.ShoppingItemStatus;
import com.autosecretary.features.meal.domain.ShoppingListItem;
import com.autosecretary.features.meal.domain.WeeklyFoodTarget;
import com.autosecretary.features.meal.domain.WeeklyFoodTargetService;
import com.autosecretary.features.meal.domain.internal.HouseholdEnergyService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Application-layer coordinator for the meal-planner screen.
 */
public class MealPlannerPresenter {

    public static final class WeeklyProgressFoodGroup {
        public final Ingredient.FoodGroup foodGroup;
        public final int targetGrams;
        public final int actualGrams;
        public final int remainingGrams;
        public final int completionPercent;

        private WeeklyProgressFoodGroup(Ingredient.FoodGroup foodGroup,
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

    public static final class WeeklyProgressOverview {
        public final LocalDate fromDate;
        public final LocalDate toDate;
        public final int calorieTarget;
        public final int calorieActual;
        public final int calorieRemaining;
        public final int calorieCompletionPercent;
        public final List<WeeklyProgressFoodGroup> foodGroups;

        private WeeklyProgressOverview(LocalDate fromDate,
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

    public void getWeeklyProgressOverview(Consumer<WeeklyProgressOverview> onLoaded) {
        workerExecutor.execute(() -> {
            LocalDate today = LocalDate.now();
            LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
            LocalDate weekEnd = weekStart.plusDays(6);
            String periodKey = weekStart.toString();

            WeeklyFoodTarget weeklyTarget = mealRepository.findWeeklyFoodTarget(periodKey);
            List<HouseholdMember> members = mealRepository.getHouseholdMembers();
            if (weeklyTarget == null) {
                weeklyTarget = WeeklyFoodTargetService.calculate(periodKey, members, today);
                mealRepository.saveWeeklyFoodTarget(weeklyTarget);
            }

            int calorieTarget = members.stream()
                    .filter(member -> member != null && member.isActive)
                    .mapToInt(member -> HouseholdEnergyService.calculateTdee(member, today))
                    .sum() * 7;

            List<ConsumptionLog> consumptionLogs = mealRepository.getConsumptionLogs(weekStart, weekEnd);
            int calorieActual = consumptionLogs.stream().mapToInt(log -> Math.max(0, log.calories)).sum();
            int calorieCompletionPercent = toPercent(calorieActual, calorieTarget);

            List<WeeklyProgressFoodGroup> groups = new ArrayList<>();
            for (Ingredient.FoodGroup foodGroup : Ingredient.FoodGroup.values()) {
                if (foodGroup == Ingredient.FoodGroup.OTHER) continue;
                int targetGrams = Math.max(0, weeklyTarget.getTargetFor(foodGroup));
                int actualGrams = estimateActualFromCalories(targetGrams, calorieActual, calorieTarget);
                int remainingGrams = Math.max(0, targetGrams - actualGrams);
                int completionPercent = toPercent(actualGrams, targetGrams);
                groups.add(new WeeklyProgressFoodGroup(foodGroup, targetGrams, actualGrams, remainingGrams,
                        completionPercent));
            }
            groups.sort(Comparator.comparing(group -> group.foodGroup.ordinal()));

            WeeklyProgressOverview overview = new WeeklyProgressOverview(
                    weekStart,
                    weekEnd,
                    calorieTarget,
                    calorieActual,
                    Math.max(0, calorieTarget - calorieActual),
                    calorieCompletionPercent,
                    groups
            );
            callbackDispatcher.execute(() -> onLoaded.accept(overview));
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

    // ── Management CRUD (Verwalten mode) ──────────────────────────────

    public void loadRecipesForManagement(Consumer<List<Recipe>> onLoaded) {
        workerExecutor.execute(() -> {
            List<Recipe> recipes = loadSortedRecipes();
            callbackDispatcher.execute(() -> onLoaded.accept(recipes));
        });
    }

    public void loadIngredientsForManagement(Consumer<List<Ingredient>> onLoaded) {
        workerExecutor.execute(() -> {
            List<Ingredient> items = new ArrayList<>(recipeRepository.getIngredients());
            items.sort(Comparator.comparing(i -> i.name));
            callbackDispatcher.execute(() -> onLoaded.accept(items));
        });
    }

    public void loadPantryItemsForManagement(Consumer<List<PantryItem>> onLoaded) {
        workerExecutor.execute(() -> {
            List<PantryItem> items = loadSortedPantryItems();
            callbackDispatcher.execute(() -> onLoaded.accept(items));
        });
    }

    public void loadHouseholdMembersForManagement(Consumer<List<HouseholdMember>> onLoaded) {
        workerExecutor.execute(() -> {
            List<HouseholdMember> members = mealRepository.getHouseholdMembers();
            members.sort(Comparator.comparing(m -> m.name));
            callbackDispatcher.execute(() -> onLoaded.accept(members));
        });
    }

    public void saveRecipe(Recipe recipe, Runnable onDone) {
        workerExecutor.execute(() -> {
            recipeRepository.saveRecipe(recipe);
            callbackDispatcher.execute(onDone);
        });
    }

    public void deleteRecipe(String recipeId, Runnable onDone) {
        workerExecutor.execute(() -> {
            recipeRepository.deleteRecipe(recipeId);
            callbackDispatcher.execute(onDone);
        });
    }

    public void saveIngredient(Ingredient ingredient, Runnable onDone) {
        workerExecutor.execute(() -> {
            recipeRepository.saveIngredient(ingredient);
            callbackDispatcher.execute(onDone);
        });
    }

    public void deleteIngredient(String ingredientId, Runnable onDone) {
        workerExecutor.execute(() -> {
            recipeRepository.deleteIngredient(ingredientId);
            callbackDispatcher.execute(onDone);
        });
    }

    public void savePantryItem(PantryItem pantryItem, Runnable onDone) {
        workerExecutor.execute(() -> {
            pantryRepository.savePantryItem(pantryItem);
            callbackDispatcher.execute(onDone);
        });
    }

    public void deletePantryItem(String pantryItemId, Runnable onDone) {
        workerExecutor.execute(() -> {
            pantryRepository.deletePantryItem(pantryItemId);
            callbackDispatcher.execute(onDone);
        });
    }

    public void saveHouseholdMember(HouseholdMember member, Runnable onDone) {
        workerExecutor.execute(() -> {
            mealRepository.saveHouseholdMember(member);
            callbackDispatcher.execute(onDone);
        });
    }

    public void deleteHouseholdMember(String memberId, Runnable onDone) {
        workerExecutor.execute(() -> {
            mealRepository.deleteHouseholdMember(memberId);
            callbackDispatcher.execute(onDone);
        });
    }

    public void deleteMealPlan(String mealPlanId, Runnable onDone) {
        workerExecutor.execute(() -> {
            mealRepository.deleteMealPlan(mealPlanId);
            callbackDispatcher.execute(onDone);
        });
    }

    public void loadCookingPreferences(Consumer<CookingPreferences> onLoaded) {
        workerExecutor.execute(() -> {
            CookingPreferences prefs = mealRepository.getCookingPreferences();
            callbackDispatcher.execute(() -> onLoaded.accept(prefs));
        });
    }

    public void saveCookingPreferences(CookingPreferences prefs, Runnable onDone) {
        workerExecutor.execute(() -> {
            mealRepository.saveCookingPreferences(prefs);
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

    private int estimateActualFromCalories(int target, int actualCalories, int targetCalories) {
        if (target <= 0 || actualCalories <= 0 || targetCalories <= 0) return 0;
        return (int) Math.round((target * (double) actualCalories) / targetCalories);
    }

    private int toPercent(int actual, int target) {
        if (target <= 0) return 0;
        return (int) Math.round((actual * 100.0) / target);
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
