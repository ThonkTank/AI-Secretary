package com.autosecretary.features.meal.application;

import com.autosecretary.features.meal.domain.MealPlan;
import com.autosecretary.features.meal.domain.MealRepository;
import com.autosecretary.shared.MealType;
import com.autosecretary.features.meal.domain.Recipe;
import com.autosecretary.features.meal.domain.RecipeRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;

public final class MealPlanMutationUseCase {
    private final MealRepository mealRepository;
    private final RecipeRepository recipeRepository;

    public MealPlanMutationUseCase(MealRepository mealRepository,
                                   RecipeRepository recipeRepository) {
        this.mealRepository = mealRepository;
        this.recipeRepository = recipeRepository;
    }

    public void planRecipe(String recipeId, LocalDate date, MealType mealType, int servings) {
        Recipe recipe = recipeRepository.findRecipeById(recipeId);
        if (recipe == null) {
            return;
        }
        MealPlan plan = new MealPlan.Builder(date, mealType, recipeId)
                .servings(Math.max(1, servings))
                .recipeTitle(recipe.title)
                .calories(recipe.totalCalories)
                .build();
        mealRepository.saveMealPlan(plan);
    }

    public void toggleMealCompleted(String mealPlanId) {
        MealPlan plan = mealRepository.findMealPlanById(mealPlanId);
        if (plan == null) {
            return;
        }
        plan.isCompleted = !plan.isCompleted;
        plan.completedAt = plan.isCompleted ? LocalDateTime.now() : null;
        if (plan.isCompleted) {
            plan.actualServings = Math.max(1, plan.plannedServings);
        }
        mealRepository.saveMealPlan(plan);
    }

    public void deleteMealPlan(String mealPlanId) {
        mealRepository.deleteMealPlan(mealPlanId);
    }
}
