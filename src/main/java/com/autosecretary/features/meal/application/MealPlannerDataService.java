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
import com.autosecretary.features.meal.domain.RecipeScalingResult;
import com.autosecretary.features.meal.domain.RecipeScalingService;
import com.autosecretary.features.meal.domain.ShoppingItemStatus;
import com.autosecretary.features.meal.domain.internal.HouseholdEnergyService;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

public final class MealPlannerDataService {
    private final MealRepository mealRepository;
    private final RecipeRepository recipeRepository;
    private final PantryRepository pantryRepository;
    private final LoadMealHomeUseCase loadMealHomeUseCase;
    private final LoadMealWeeklyProgressUseCase loadMealWeeklyProgressUseCase;
    private final MealPlanMutationUseCase mealPlanMutationUseCase;
    private final MealShoppingUseCase mealShoppingUseCase;
    private final Executor workerExecutor;
    private final Executor callbackDispatcher;

    public MealPlannerDataService(MealRepository mealRepository,
                                  RecipeRepository recipeRepository,
                                  PantryRepository pantryRepository,
                                  Executor workerExecutor,
                                  Executor callbackDispatcher) {
        this.mealRepository = mealRepository;
        this.recipeRepository = recipeRepository;
        this.pantryRepository = pantryRepository;
        this.workerExecutor = workerExecutor;
        this.callbackDispatcher = callbackDispatcher;
        this.loadMealHomeUseCase = new LoadMealHomeUseCase(
                mealRepository, recipeRepository, pantryRepository);
        this.loadMealWeeklyProgressUseCase = new LoadMealWeeklyProgressUseCase(mealRepository);
        this.mealPlanMutationUseCase = new MealPlanMutationUseCase(mealRepository, recipeRepository);
        this.mealShoppingUseCase = new MealShoppingUseCase(pantryRepository);
    }

    public void loadHome(Consumer<MealHomeModel> onLoaded) {
        dispatch(() -> loadMealHomeUseCase.load(LocalDate.now()), onLoaded);
    }

    public void getWeeklyProgressOverview(Consumer<WeeklyProgressOverview> onLoaded) {
        dispatch(() -> loadMealWeeklyProgressUseCase.load(LocalDate.now()), onLoaded);
    }

    public void openManagePlan(Consumer<List<Recipe>> onReady) {
        dispatch(loadMealHomeUseCase::loadSortedRecipes, onReady);
    }

    public RecipeScalingResult scaleRecipePreview(Recipe recipe, double requestedServings) {
        return RecipeScalingService.scaleRecipe(recipe, requestedServings);
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
        dispatch(loadMealHomeUseCase::loadSortedRecipes, onLoaded);
    }

    public void loadIngredientsForManagement(Consumer<List<Ingredient>> onLoaded) {
        dispatch(this::sortedIngredients, onLoaded);
    }

    public void loadPantryItemsForManagement(Consumer<List<PantryItem>> onLoaded) {
        dispatch(loadMealHomeUseCase::loadSortedPantryItems, onLoaded);
    }

    public void loadHouseholdMembersForManagement(Consumer<List<HouseholdMember>> onLoaded) {
        dispatch(this::sortedHouseholdMembers, onLoaded);
    }

    public void loadHouseholdMemberOverviewsForManagement(Consumer<List<HouseholdMemberOverview>> onLoaded) {
        dispatch(() -> householdMemberOverviews(LocalDate.now()), onLoaded);
    }

    public void saveRecipe(Recipe recipe, Runnable onDone) {
        runMutation(() -> recipeRepository.saveRecipe(recipe), onDone);
    }

    public void deleteRecipe(String recipeId, Runnable onDone) {
        runMutation(() -> recipeRepository.deleteRecipe(recipeId), onDone);
    }

    public void saveIngredient(Ingredient ingredient, Runnable onDone) {
        runMutation(() -> recipeRepository.saveIngredient(ingredient), onDone);
    }

    public void deleteIngredient(String ingredientId, Runnable onDone) {
        runMutation(() -> recipeRepository.deleteIngredient(ingredientId), onDone);
    }

    public void savePantryItem(PantryItem pantryItem, Runnable onDone) {
        runMutation(() -> pantryRepository.savePantryItem(pantryItem), onDone);
    }

    public void deletePantryItem(String pantryItemId, Runnable onDone) {
        runMutation(() -> pantryRepository.deletePantryItem(pantryItemId), onDone);
    }

    public void saveHouseholdMember(HouseholdMember member, Runnable onDone) {
        runMutation(() -> mealRepository.saveHouseholdMember(member), onDone);
    }

    public void deleteHouseholdMember(String memberId, Runnable onDone) {
        runMutation(() -> mealRepository.deleteHouseholdMember(memberId), onDone);
    }

    public void deleteMealPlan(String mealPlanId, Runnable onDone) {
        runMutation(() -> mealPlanMutationUseCase.deleteMealPlan(mealPlanId), onDone);
    }

    public void loadCookingPreferences(Consumer<CookingPreferences> onLoaded) {
        dispatch(mealRepository::getCookingPreferences, onLoaded);
    }

    public void saveCookingPreferences(CookingPreferences prefs, Runnable onDone) {
        runMutation(() -> mealRepository.saveCookingPreferences(prefs), onDone);
    }

    private List<Ingredient> sortedIngredients() {
        List<Ingredient> items = new ArrayList<>(recipeRepository.getIngredients());
        items.sort(Comparator.comparing(i -> i.name));
        return items;
    }

    private List<HouseholdMember> sortedHouseholdMembers() {
        List<HouseholdMember> members = mealRepository.getHouseholdMembers();
        members.sort(Comparator.comparing(m -> m.name));
        return members;
    }

    private List<HouseholdMemberOverview> householdMemberOverviews(LocalDate today) {
        return mealRepository.getHouseholdMembers().stream()
                .sorted(Comparator.comparing(member -> member.name))
                .map(member -> new HouseholdMemberOverview(
                        member,
                        HouseholdEnergyService.calculateAge(member, today),
                        HouseholdEnergyService.calculateTdee(member, today)))
                .toList();
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
