package com.autosecretary.features.task.application.internal.meal;

import com.autosecretary.features.meal.domain.ConsumptionLog;
import com.autosecretary.features.meal.domain.MealPlan;
import com.autosecretary.features.meal.domain.MealRepository;
import com.autosecretary.features.meal.domain.PantryItem;
import com.autosecretary.features.meal.domain.PantryRepository;
import com.autosecretary.features.meal.domain.Recipe;
import com.autosecretary.features.meal.domain.RecipeRepository;
import com.autosecretary.features.meal.domain.RecipeScalingResult;
import com.autosecretary.features.meal.domain.RecipeScalingService;
import com.autosecretary.features.task.domain.TaskMealCompletionRequest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class TaskMealCompletionFromMealPlanner {
    private static final double DEPLETION_EPSILON = 0.00001;

    private final MealRepository mealRepository;
    private final RecipeRepository recipeRepository;
    private final PantryRepository pantryRepository;

    public TaskMealCompletionFromMealPlanner(MealRepository mealRepository,
                                             RecipeRepository recipeRepository,
                                             PantryRepository pantryRepository) {
        this.mealRepository = Objects.requireNonNull(mealRepository, "mealRepository");
        this.recipeRepository = Objects.requireNonNull(recipeRepository, "recipeRepository");
        this.pantryRepository = Objects.requireNonNull(pantryRepository, "pantryRepository");
    }

    public void completeMeal(TaskMealCompletionRequest request) {
        Recipe recipe = recipeRepository.findRecipeById(request.recipeId());
        if (recipe == null) {
            return;
        }

        int servings = request.actualServings() > 0
                ? request.actualServings()
                : request.plannedServings();
        reducePantryStock(recipe, servings);
        writeConsumptionLog(recipe, request.completionDate(), servings);
        completeMealPlanEntry(request, servings);
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

    private void consumeIngredientFromPantry(Recipe.RecipeIngredient ingredient,
                                             List<PantryItem> pantryItems,
                                             double scaleFactor) {
        if (ingredient.ingredientId() == null) {
            return;
        }
        double requiredAmount = ingredient.amount() * scaleFactor;
        if (requiredAmount <= 0) {
            return;
        }
        for (PantryItem pantryItem : pantryItems) {
            if (requiredAmount <= 0) break;
            if (pantryItem.id == null || !Objects.equals(pantryItem.ingredientId, ingredient.ingredientId())) {
                continue;
            }
            double consumed = Math.min(pantryItem.amount, requiredAmount);
            pantryItem.amount -= consumed;
            requiredAmount -= consumed;

            if (pantryItem.amount <= DEPLETION_EPSILON) {
                pantryRepository.deletePantryItem(pantryItem.id);
            } else {
                pantryRepository.savePantryItem(pantryItem);
            }
        }
    }

    private void writeConsumptionLog(Recipe recipe, LocalDate completionDate, int servings) {
        double scale = recipe.servings > 0 ? (double) servings / recipe.servings : servings;
        int calories = (int) Math.round(recipe.totalCalories * scale);
        int protein = (int) Math.round(recipe.totalProtein * scale);
        int carbs = (int) Math.round(recipe.totalCarbs * scale);
        int fat = (int) Math.round(recipe.totalFat * scale);

        ConsumptionLog log = new ConsumptionLog.Builder(completionDate, null, null)
                .recipeId(recipe.id)
                .servings(servings)
                .calories(calories)
                .protein(protein)
                .carbs(carbs)
                .fat(fat)
                .build();
        mealRepository.saveConsumptionLog(log);
    }

    private void completeMealPlanEntry(TaskMealCompletionRequest request, int servings) {
        List<MealPlan> plans = mealRepository.getMealPlans(request.completionDate(), request.completionDate());
        plans.stream()
                .filter(plan -> plan.mealType == request.mealType()
                        && Objects.equals(plan.recipeId, request.recipeId()))
                .findFirst()
                .ifPresent(plan -> {
                    plan.isCompleted = true;
                    plan.actualServings = servings;
                    plan.completedAt = LocalDateTime.now();
                    mealRepository.saveMealPlan(plan);
                });
    }
}
