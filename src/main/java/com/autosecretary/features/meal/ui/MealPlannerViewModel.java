package com.autosecretary.features.meal.ui;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.autosecretary.features.meal.application.MealPlannerPresenter;
import com.autosecretary.features.meal.domain.HouseholdMember;
import com.autosecretary.features.meal.domain.Ingredient;
import com.autosecretary.features.meal.domain.MealPlan;
import com.autosecretary.features.meal.domain.MealType;
import com.autosecretary.features.meal.domain.PantryItem;
import com.autosecretary.features.meal.domain.Recipe;
import com.autosecretary.features.meal.domain.ShoppingItemStatus;
import com.autosecretary.features.meal.ui.state.HomeState;
import com.autosecretary.features.meal.ui.state.WeeklyProgressFoodGroupState;
import com.autosecretary.features.meal.ui.state.WeeklyProgressState;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Consumer;

/**
 * ViewModel owner for the meal-planner surface.
 */
public class MealPlannerViewModel extends ViewModel {
    private final MealPlannerPresenter presenter;
    private final MutableLiveData<HomeState> homeState = new MutableLiveData<>();
    private final MutableLiveData<WeeklyProgressState> weeklyProgress =
            new MutableLiveData<>();

    public MealPlannerViewModel(MealPlannerPresenter presenter) {
        this.presenter = presenter;
    }

    public LiveData<HomeState> getHomeState() {
        return homeState;
    }

    public LiveData<WeeklyProgressState> getWeeklyProgress() {
        return weeklyProgress;
    }

    public void reloadHome() {
        presenter.loadHome(home -> homeState.setValue(new HomeState(
                home.weekPlans,
                home.shoppingItems)));
    }

    public void reloadWeeklyProgress() {
        presenter.getWeeklyProgressOverview(progress -> weeklyProgress.setValue(new WeeklyProgressState(
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
        presenter.openManagePlan(onReady);
    }

    public void openManageNeed(Runnable onReady) {
        presenter.openManageNeed(onReady);
    }

    public void loadRecipesForManagement(Consumer<List<Recipe>> onLoaded) {
        presenter.loadRecipesForManagement(onLoaded);
    }

    public void loadIngredientsForManagement(Consumer<List<Ingredient>> onLoaded) {
        presenter.loadIngredientsForManagement(onLoaded);
    }

    public void loadPantryItemsForManagement(Consumer<List<PantryItem>> onLoaded) {
        presenter.loadPantryItemsForManagement(onLoaded);
    }

    public void loadHouseholdMembersForManagement(Consumer<List<HouseholdMember>> onLoaded) {
        presenter.loadHouseholdMembersForManagement(onLoaded);
    }

    public void planRecipe(
            String recipeId,
            LocalDate date,
            MealType mealType,
            int servings,
            Runnable onDone) {
        presenter.planRecipe(recipeId, date, mealType, servings, () -> {
            reloadHome();
            onDone.run();
        });
    }

    public void createShoppingItemFromNeed(
            String ingredientName,
            double neededAmount,
            String unit,
            Runnable onDone) {
        presenter.createShoppingItemFromNeed(ingredientName, neededAmount, unit, () -> {
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
        presenter.createPantryItem(ingredientName, amount, unit, location, shelfLifeDays, onDone);
    }

    public void savePantryItem(PantryItem item, Runnable onDone) {
        presenter.savePantryItem(item, onDone);
    }

    public void deletePantryItem(String pantryItemId, Runnable onDone) {
        presenter.deletePantryItem(pantryItemId, onDone);
    }

    public void saveRecipe(Recipe recipe, Runnable onDone) {
        presenter.saveRecipe(recipe, onDone);
    }

    public void deleteRecipe(String recipeId, Runnable onDone) {
        presenter.deleteRecipe(recipeId, onDone);
    }

    public void saveIngredient(Ingredient ingredient, Runnable onDone) {
        presenter.saveIngredient(ingredient, onDone);
    }

    public void deleteIngredient(String ingredientId, Runnable onDone) {
        presenter.deleteIngredient(ingredientId, onDone);
    }

    public void saveHouseholdMember(HouseholdMember member, Runnable onDone) {
        presenter.saveHouseholdMember(member, onDone);
    }

    public void deleteHouseholdMember(String memberId, Runnable onDone) {
        presenter.deleteHouseholdMember(memberId, onDone);
    }

    public void toggleMealCompleted(String mealPlanId) {
        presenter.toggleMealCompleted(mealPlanId, () -> {
            reloadHome();
            reloadWeeklyProgress();
        });
    }

    public void updateShoppingItemStatus(String shoppingItemId, ShoppingItemStatus status) {
        presenter.updateShoppingItemStatus(shoppingItemId, status, () -> {
            reloadHome();
            reloadWeeklyProgress();
        });
    }
}
