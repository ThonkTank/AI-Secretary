package com.autosecretary.features.meal.ui;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.autosecretary.features.meal.application.MealPlannerDataService;
import com.autosecretary.features.meal.domain.HouseholdMember;
import com.autosecretary.features.meal.domain.Ingredient;
import com.autosecretary.features.meal.domain.MealPlan;
import com.autosecretary.shared.MealType;
import com.autosecretary.features.meal.domain.PantryItem;
import com.autosecretary.features.meal.domain.Recipe;
import com.autosecretary.features.meal.domain.RecipeScalingResult;
import com.autosecretary.features.meal.domain.ShoppingItemStatus;
import com.autosecretary.features.meal.ui.state.HomeState;
import com.autosecretary.features.meal.ui.state.HouseholdMemberRowState;
import com.autosecretary.features.meal.ui.state.WeeklyProgressFoodGroupState;
import com.autosecretary.features.meal.ui.state.WeeklyProgressState;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Consumer;

/**
 * ViewModel owner for the meal-planner surface.
 */
public class MealPlannerViewModel extends ViewModel {
    private final MealPlannerDataService dataService;
    private final MutableLiveData<HomeState> homeState = new MutableLiveData<>();
    private final MutableLiveData<WeeklyProgressState> weeklyProgress =
            new MutableLiveData<>();

    public MealPlannerViewModel(MealPlannerDataService dataService) {
        this.dataService = dataService;
    }

    public LiveData<HomeState> getHomeState() {
        return homeState;
    }

    public LiveData<WeeklyProgressState> getWeeklyProgress() {
        return weeklyProgress;
    }

    public void reloadHome() {
        dataService.loadHome(home -> homeState.setValue(new HomeState(
                home.weekPlans,
                home.shoppingItems)));
    }

    public void reloadWeeklyProgress() {
        dataService.getWeeklyProgressOverview(progress -> weeklyProgress.setValue(new WeeklyProgressState(
                progress.fromDate,
                progress.toDate,
                progress.calorieActual,
                progress.calorieTarget,
                progress.calorieCompletionPercent,
                progress.foodGroups.stream()
                        .map(group -> new WeeklyProgressFoodGroupState(
                                group.foodGroup,
                                group.completionPercent))
                        .toList())));
    }

    public void openManagePlan(Consumer<List<Recipe>> onReady) {
        dataService.openManagePlan(onReady);
    }

    public RecipeScalingResult scaleRecipePreview(Recipe recipe, double requestedServings) {
        return dataService.scaleRecipePreview(recipe, requestedServings);
    }

    public void loadRecipesForManagement(Consumer<List<Recipe>> onLoaded) {
        dataService.loadRecipesForManagement(onLoaded);
    }

    public void loadIngredientsForManagement(Consumer<List<Ingredient>> onLoaded) {
        dataService.loadIngredientsForManagement(onLoaded);
    }

    public void loadPantryItemsForManagement(Consumer<List<PantryItem>> onLoaded) {
        dataService.loadPantryItemsForManagement(onLoaded);
    }

    public void loadHouseholdMembersForManagement(Consumer<List<HouseholdMember>> onLoaded) {
        dataService.loadHouseholdMembersForManagement(onLoaded);
    }

    public void loadHouseholdMemberRowsForManagement(Consumer<List<HouseholdMemberRowState>> onLoaded) {
        dataService.loadHouseholdMemberOverviewsForManagement(overviews -> onLoaded.accept(overviews.stream()
                .map(overview -> new HouseholdMemberRowState(
                        overview.member(),
                        overview.age(),
                        overview.tdee()))
                .toList()));
    }

    public void planRecipe(
            String recipeId,
            LocalDate date,
            MealType mealType,
            int servings,
            Runnable onDone) {
        dataService.planRecipe(recipeId, date, mealType, servings, () -> {
            reloadHome();
            onDone.run();
        });
    }

    public void createShoppingItemFromNeed(
            String ingredientName,
            double neededAmount,
            String unit,
            Runnable onDone) {
        dataService.createShoppingItemFromNeed(ingredientName, neededAmount, unit, () -> {
            reloadHome();
            reloadWeeklyProgress();
            onDone.run();
        });
    }

    public void createPantryItem(
            String ingredientName,
            double amount,
            String unit,
            PantryItem.StorageLocation location,
            int shelfLifeDays,
            Runnable onDone) {
        dataService.createPantryItem(ingredientName, amount, unit, location, shelfLifeDays, onDone);
    }

    public void savePantryItem(PantryItem item, Runnable onDone) {
        dataService.savePantryItem(item, onDone);
    }

    public void deletePantryItem(String pantryItemId, Runnable onDone) {
        dataService.deletePantryItem(pantryItemId, onDone);
    }

    public void saveRecipe(Recipe recipe, Runnable onDone) {
        dataService.saveRecipe(recipe, onDone);
    }

    public void deleteRecipe(String recipeId, Runnable onDone) {
        dataService.deleteRecipe(recipeId, onDone);
    }

    public void saveIngredient(Ingredient ingredient, Runnable onDone) {
        dataService.saveIngredient(ingredient, onDone);
    }

    public void deleteIngredient(String ingredientId, Runnable onDone) {
        dataService.deleteIngredient(ingredientId, onDone);
    }

    public void saveHouseholdMember(HouseholdMember member, Runnable onDone) {
        dataService.saveHouseholdMember(member, onDone);
    }

    public void deleteHouseholdMember(String memberId, Runnable onDone) {
        dataService.deleteHouseholdMember(memberId, onDone);
    }

    public void toggleMealCompleted(String mealPlanId) {
        dataService.toggleMealCompleted(mealPlanId, () -> {
            reloadHome();
            reloadWeeklyProgress();
        });
    }

    public void updateShoppingItemStatus(String shoppingItemId, ShoppingItemStatus status) {
        dataService.updateShoppingItemStatus(shoppingItemId, status, () -> {
            reloadHome();
            reloadWeeklyProgress();
        });
    }
}
