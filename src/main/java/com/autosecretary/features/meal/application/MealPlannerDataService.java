package com.autosecretary.features.meal.application;

import com.autosecretary.features.meal.domain.CookingPreferences;
import com.autosecretary.features.meal.domain.HouseholdMember;
import com.autosecretary.features.meal.domain.Ingredient;
import com.autosecretary.features.meal.domain.MealRepository;
import com.autosecretary.shared.MealType;
import com.autosecretary.features.meal.domain.PantryItem;
import com.autosecretary.features.meal.domain.PantryRepository;
import com.autosecretary.features.meal.domain.Recipe;
import com.autosecretary.features.meal.domain.RecipeRepository;
import com.autosecretary.features.meal.domain.ShoppingItemStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

public final class MealPlannerDataService {
    private final LoadMealHomeUseCase loadMealHomeUseCase;
    private final LoadMealWeeklyProgressUseCase loadMealWeeklyProgressUseCase;
    private final MealPlanMutationUseCase mealPlanMutationUseCase;
    private final MealShoppingUseCase mealShoppingUseCase;
    private final MealManagementDataService mealManagementDataService;
    private final Executor workerExecutor;
    private final Executor callbackDispatcher;

    public MealPlannerDataService(MealRepository mealRepository,
                                  RecipeRepository recipeRepository,
                                  PantryRepository pantryRepository,
                                  Executor workerExecutor,
                                  Executor callbackDispatcher) {
        this.workerExecutor = workerExecutor;
        this.callbackDispatcher = callbackDispatcher;
        this.loadMealHomeUseCase = new LoadMealHomeUseCase(
                mealRepository, recipeRepository, pantryRepository);
        this.loadMealWeeklyProgressUseCase = new LoadMealWeeklyProgressUseCase(mealRepository);
        this.mealPlanMutationUseCase = new MealPlanMutationUseCase(mealRepository, recipeRepository);
        this.mealShoppingUseCase = new MealShoppingUseCase(pantryRepository);
        this.mealManagementDataService = new MealManagementDataService(
                mealRepository, recipeRepository, pantryRepository, loadMealHomeUseCase);
    }

    public void loadHome(Consumer<MealHomeModel> onLoaded) {
        dispatch(() -> loadMealHomeUseCase.load(LocalDate.now()), onLoaded);
    }

    public void getWeeklyProgressOverview(Consumer<WeeklyProgressOverview> onLoaded) {
        dispatch(() -> loadMealWeeklyProgressUseCase.load(LocalDate.now()), onLoaded);
    }

    public void openManagePlan(Consumer<List<Recipe>> onReady) {
        dispatch(mealManagementDataService::loadRecipesForPlanManagement, onReady);
    }

    public void openManageNeed(Runnable onReady) {
        callbackDispatcher.execute(onReady);
    }

    public void openManagePantry(Runnable onReady) {
        callbackDispatcher.execute(onReady);
    }

    public void updateShoppingItemStatus(String shoppingItemId, ShoppingItemStatus status, Runnable onDone) {
        runMutation(() -> mealShoppingUseCase.updateShoppingItemStatus(shoppingItemId, status), onDone);
    }

    public void planRecipe(String recipeId, LocalDate date, MealType mealType, int servings,
                           Runnable onDone) {
        runMutation(() -> mealPlanMutationUseCase.planRecipe(recipeId, date, mealType, servings), onDone);
    }

    public void toggleMealCompleted(String mealPlanId, Runnable onDone) {
        runMutation(() -> mealPlanMutationUseCase.toggleMealCompleted(mealPlanId), onDone);
    }

    public void createShoppingItemFromNeed(String ingredientName, double neededAmount, String unit,
                                           Runnable onDone) {
        runMutation(() -> mealShoppingUseCase.createShoppingItemFromNeed(ingredientName, neededAmount, unit), onDone);
    }

    public void createPantryItem(String ingredientName,
                                 double amount,
                                 String unit,
                                 PantryItem.StorageLocation location,
                                 int shelfLifeDays,
                                 Runnable onDone) {
        runMutation(() -> mealShoppingUseCase.createPantryItem(
                ingredientName, amount, unit, location, shelfLifeDays), onDone);
    }

    public void loadRecipesForManagement(Consumer<List<Recipe>> onLoaded) {
        dispatch(mealManagementDataService::loadRecipesForManagement, onLoaded);
    }

    public void loadIngredientsForManagement(Consumer<List<Ingredient>> onLoaded) {
        dispatch(mealManagementDataService::loadIngredientsForManagement, onLoaded);
    }

    public void loadPantryItemsForManagement(Consumer<List<PantryItem>> onLoaded) {
        dispatch(mealManagementDataService::loadPantryItemsForManagement, onLoaded);
    }

    public void loadHouseholdMembersForManagement(Consumer<List<HouseholdMember>> onLoaded) {
        dispatch(mealManagementDataService::loadHouseholdMembersForManagement, onLoaded);
    }

    public void loadHouseholdMemberOverviewsForManagement(Consumer<List<HouseholdMemberOverview>> onLoaded) {
        dispatch(() -> mealManagementDataService.loadHouseholdMemberOverviewsForManagement(LocalDate.now()), onLoaded);
    }

    public void saveRecipe(Recipe recipe, Runnable onDone) {
        runMutation(() -> mealManagementDataService.saveRecipe(recipe), onDone);
    }

    public void deleteRecipe(String recipeId, Runnable onDone) {
        runMutation(() -> mealManagementDataService.deleteRecipe(recipeId), onDone);
    }

    public void saveIngredient(Ingredient ingredient, Runnable onDone) {
        runMutation(() -> mealManagementDataService.saveIngredient(ingredient), onDone);
    }

    public void deleteIngredient(String ingredientId, Runnable onDone) {
        runMutation(() -> mealManagementDataService.deleteIngredient(ingredientId), onDone);
    }

    public void savePantryItem(PantryItem pantryItem, Runnable onDone) {
        runMutation(() -> mealManagementDataService.savePantryItem(pantryItem), onDone);
    }

    public void deletePantryItem(String pantryItemId, Runnable onDone) {
        runMutation(() -> mealManagementDataService.deletePantryItem(pantryItemId), onDone);
    }

    public void saveHouseholdMember(HouseholdMember member, Runnable onDone) {
        runMutation(() -> mealManagementDataService.saveHouseholdMember(member), onDone);
    }

    public void deleteHouseholdMember(String memberId, Runnable onDone) {
        runMutation(() -> mealManagementDataService.deleteHouseholdMember(memberId), onDone);
    }

    public void deleteMealPlan(String mealPlanId, Runnable onDone) {
        runMutation(() -> mealPlanMutationUseCase.deleteMealPlan(mealPlanId), onDone);
    }

    public void loadCookingPreferences(Consumer<CookingPreferences> onLoaded) {
        dispatch(mealManagementDataService::loadCookingPreferences, onLoaded);
    }

    public void saveCookingPreferences(CookingPreferences prefs, Runnable onDone) {
        runMutation(() -> mealManagementDataService.saveCookingPreferences(prefs), onDone);
    }

    private <T> void dispatch(ThrowingSupplier<T> supplier, Consumer<T> callback) {
        workerExecutor.execute(() -> {
            T result = supplier.get();
            callbackDispatcher.execute(() -> callback.accept(result));
        });
    }

    private void runMutation(Runnable mutation, Runnable onDone) {
        workerExecutor.execute(() -> {
            mutation.run();
            callbackDispatcher.execute(onDone);
        });
    }

    private interface ThrowingSupplier<T> {
        T get();
    }
}
