package com.autosecretary.features.meal.application;

import com.autosecretary.features.meal.domain.ConsumptionLog;
import com.autosecretary.features.meal.domain.MealPlan;
import com.autosecretary.features.meal.domain.MealRepository;
import com.autosecretary.features.meal.domain.PantryItem;
import com.autosecretary.features.meal.domain.PantryRepository;
import com.autosecretary.features.meal.domain.Recipe;
import com.autosecretary.features.meal.domain.RecipeRepository;
import com.autosecretary.features.meal.domain.internal.RecipeScalingService;
import com.autosecretary.features.task.data.Task;
import com.autosecretary.features.task.data.TaskPlannedMeal;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Encapsulates meal-specific task completion behavior so UI controllers stay lean.
 */
public class TaskMealIntegrationService {

    private static final long DEFAULT_MEMBER_ID = 0L;

    private final MealRepository mealRepository;
    private final RecipeRepository recipeRepository;
    private final PantryRepository pantryRepository;
    private final RecipeScalingService recipeScalingService;

    public TaskMealIntegrationService(MealRepository mealRepository,
                                      RecipeRepository recipeRepository,
                                      PantryRepository pantryRepository) {
        this.mealRepository = mealRepository;
        this.recipeRepository = recipeRepository;
        this.pantryRepository = pantryRepository;
        this.recipeScalingService = new RecipeScalingService();
    }

    public TaskPlannedMeal resolvePlannedMeal(Task task, LocalDate date) {
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

        applyFollowUpActions(task, plannedMeal, completionDate, servings);
        return true;
    }

    private void applyFollowUpActions(Task task,
                                      TaskPlannedMeal plannedMeal,
                                      LocalDate completionDate,
                                      int servings) {
        Recipe recipe = recipeRepository.findRecipeById(plannedMeal.recipeId);
        if (recipe == null) {
            return;
        }

        reducePantryStock(recipe, servings);
        writeConsumptionLog(task, recipe, completionDate, servings);
        completeMealPlanEntry(task, plannedMeal, completionDate, servings);
    }

    private void reducePantryStock(Recipe recipe, int servings) {
        if (recipe.ingredients == null || recipe.ingredients.isEmpty()) {
            return;
        }

        List<PantryItem> pantryItems = new ArrayList<>(pantryRepository.getPantryItems());
        pantryItems.sort(Comparator.comparing(item -> item.expiryDate, Comparator.nullsLast(Comparator.naturalOrder())));

        RecipeScalingService.ScalingResult scalingResult = recipeScalingService.scaleRecipe(recipe, servings);
        double factor = Math.max(0.0, scalingResult.factor());

        for (Recipe.RecipeIngredient ingredient : recipe.ingredients) {
            if (ingredient.ingredientId() == null) {
                continue;
            }
            double requiredAmount = ingredient.amount() * factor;
            if (requiredAmount <= 0) {
                continue;
            }
            for (PantryItem pantryItem : pantryItems) {
                if (pantryItem.id == null || pantryItem.ingredientId != ingredient.ingredientId()) {
                    continue;
                }
                if (requiredAmount <= 0) {
                    break;
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
    }

    private void writeConsumptionLog(Task task, Recipe recipe, LocalDate completionDate, int servings) {
        double scale = recipe.servings > 0 ? (double) servings / recipe.servings : servings;
        int calories = (int) Math.round(recipe.totalCalories * scale);
        int protein = (int) Math.round(recipe.totalProtein * scale);
        int carbs = (int) Math.round(recipe.totalCarbs * scale);
        int fat = (int) Math.round(recipe.totalFat * scale);

        ConsumptionLog log = new ConsumptionLog.Builder(completionDate, parseTaskId(task.core.id), DEFAULT_MEMBER_ID)
                .recipeId(recipe.id != null ? recipe.id : 0L)
                .servings(servings)
                .calories(calories)
                .protein(protein)
                .carbs(carbs)
                .fat(fat)
                .build();
        mealRepository.saveConsumptionLog(log);
    }

    private void completeMealPlanEntry(Task task, TaskPlannedMeal plannedMeal, LocalDate completionDate, int servings) {
        List<MealPlan> plans = mealRepository.getMealPlans(completionDate, completionDate);
        for (MealPlan plan : plans) {
            if (plan.date == null || !completionDate.equals(plan.date)) {
                continue;
            }
            if (plan.mealType != task.core.mealType || plan.recipeId != plannedMeal.recipeId) {
                continue;
            }
            plan.isCompleted = true;
            plan.actualServings = servings;
            plan.completedAt = LocalDateTime.now();
            mealRepository.saveMealPlan(plan);
            return;
        }
    }

    private long parseTaskId(String taskId) {
        if (taskId == null) {
            return 0L;
        }
        try {
            return Long.parseLong(taskId);
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }
}
