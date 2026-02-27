package com.autosecretary.features.meal.application;

import com.autosecretary.features.meal.domain.ConsumptionLog;
import com.autosecretary.features.meal.domain.MealPlan;
import com.autosecretary.features.meal.domain.MealRepository;
import com.autosecretary.features.meal.domain.PantryItem;
import com.autosecretary.features.meal.domain.PantryRepository;
import com.autosecretary.features.meal.domain.Recipe;
import com.autosecretary.features.meal.domain.RecipeRepository;
import com.autosecretary.features.meal.domain.MealType;
import com.autosecretary.features.meal.domain.RecipeScalingResult;
import com.autosecretary.features.meal.domain.RecipeScalingService;
import com.autosecretary.features.task.data.Task;
import com.autosecretary.features.task.data.TaskPlannedMeal;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Encapsulates meal-specific task completion behavior so UI controllers stay lean.
 */
public class TaskMealIntegrationService {

    private static final long DEFAULT_MEMBER_ID = 0L;

    private final MealRepository mealRepository;
    private final RecipeRepository recipeRepository;
    private final PantryRepository pantryRepository;

    public TaskMealIntegrationService(MealRepository mealRepository,
                                      RecipeRepository recipeRepository,
                                      PantryRepository pantryRepository) {
        this.mealRepository = mealRepository;
        this.recipeRepository = recipeRepository;
        this.pantryRepository = pantryRepository;
    }

    private TaskPlannedMeal resolvePlannedMeal(Task task, LocalDate date) {
        if (task == null || task.core == null || task.core.mealType == null || date == null) {
            return null;
        }
        return task.getPlannedMealForDate(date);
    }

    public boolean completeMealTask(Task task, LocalDate completionDate, int actualServingsOverride) {
        TaskPlannedMeal plannedMeal = resolvePlannedMeal(task, completionDate);
        if (plannedMeal == null || plannedMeal.completed) {
            return false;
        }

        int servings = actualServingsOverride > 0 ? actualServingsOverride : plannedMeal.plannedServings;
        if (!task.completePlannedMeal(completionDate, servings)) {
            return false;
        }

        applyFollowUpActions(task.core.mealType, plannedMeal, completionDate, servings);
        return true;
    }

    private void applyFollowUpActions(MealType mealType,
                                      TaskPlannedMeal plannedMeal,
                                      LocalDate completionDate,
                                      int servings) {
        Recipe recipe = recipeRepository.findRecipeById(plannedMeal.recipeId);
        if (recipe == null) {
            return;
        }

        reducePantryStock(recipe, servings);
        writeConsumptionLog(recipe, completionDate, servings);
        completeMealPlanEntry(mealType, plannedMeal, completionDate, servings);
    }

    private void reducePantryStock(Recipe recipe, int servings) {
        if (recipe.ingredients == null || recipe.ingredients.isEmpty()) {
            return;
        }

        List<PantryItem> pantryItems = new ArrayList<>(pantryRepository.getPantryItems());
        pantryItems.sort(Comparator.nullsLast(Comparator.comparing(item -> item.expiryDate)));

        RecipeScalingResult scalingResult = RecipeScalingService.scaleRecipe(recipe, servings);
        double factor = Math.max(0.0, scalingResult.factor());

        for (Recipe.RecipeIngredient ingredient : recipe.ingredients) {
            consumeIngredientFromPantry(ingredient, pantryItems, factor);
        }
    }

    private void consumeIngredientFromPantry(Recipe.RecipeIngredient ingredient, List<PantryItem> pantryItems, double scaleFactor) {
        if (ingredient.ingredientId() == null) {
            return;
        }
        double requiredAmount = ingredient.amount() * scaleFactor;
        if (requiredAmount <= 0) {
            return;
        }
        for (PantryItem pantryItem : pantryItems) {
            if (requiredAmount <= 0) break;
            if (pantryItem.id == null || pantryItem.ingredientId != ingredient.ingredientId()) {
                continue;
            }
            double consumed = Math.min(pantryItem.amount, requiredAmount);
            pantryItem.amount -= consumed;
            requiredAmount -= consumed;

            if (pantryItem.amount <= 0.00001d) {
                pantryRepository.deletePantryItem(pantryItem.id);
            } else {
                pantryRepository.savePantryItem(pantryItem);
            }
        }
    }

    private void writeConsumptionLog(Recipe recipe, LocalDate completionDate, int servings) {
        // If the recipe has no defined base servings, treat each requested serving as one unit
        // (scale = requested count), rather than defaulting to 1.0 and ignoring the count.
        double scale = recipe.servings > 0 ? (double) servings / recipe.servings : servings;
        int calories = (int) Math.round(recipe.totalCalories * scale);
        int protein = (int) Math.round(recipe.totalProtein * scale);
        int carbs = (int) Math.round(recipe.totalCarbs * scale);
        int fat = (int) Math.round(recipe.totalFat * scale);

        ConsumptionLog log = new ConsumptionLog.Builder(completionDate, 0L, DEFAULT_MEMBER_ID)
                .recipeId(Objects.requireNonNullElse(recipe.id, 0L))
                .servings(servings)
                .calories(calories)
                .protein(protein)
                .carbs(carbs)
                .fat(fat)
                .build();
        mealRepository.saveConsumptionLog(log);
    }

    private void completeMealPlanEntry(MealType mealType, TaskPlannedMeal plannedMeal, LocalDate completionDate, int servings) {
        List<MealPlan> plans = mealRepository.getMealPlans(completionDate, completionDate);
        plans.stream()
                .filter(plan -> plan.date != null)
                .filter(plan -> plan.mealType == mealType && plan.recipeId == plannedMeal.recipeId)
                .findFirst()
                .ifPresent(plan -> {
                    plan.isCompleted = true;
                    plan.actualServings = servings;
                    plan.completedAt = LocalDateTime.now();
                    mealRepository.saveMealPlan(plan);
                });
    }

}
