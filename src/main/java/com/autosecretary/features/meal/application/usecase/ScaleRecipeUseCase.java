package com.autosecretary.features.meal.application.usecase;

import com.autosecretary.features.meal.domain.Recipe;
import com.autosecretary.features.meal.domain.RecipeRepository;
import com.autosecretary.features.meal.domain.internal.RecipeScalingService;

/**
 * Application-Use-Case fuer Rezept-Skalierung mit Precision-Regeln.
 */
public class ScaleRecipeUseCase {

    private final RecipeRepository recipeRepository;

    public ScaleRecipeUseCase(RecipeRepository recipeRepository) {
        this.recipeRepository = recipeRepository;
    }

    public RecipeScalingService.ScalingResult execute(long recipeId, double requestedServings) {
        Recipe recipe = recipeRepository.findRecipeById(recipeId);
        if (recipe == null) {
            throw new IllegalArgumentException("Recipe not found: id=" + recipeId);
        }
        return RecipeScalingService.scaleRecipe(recipe, requestedServings);
    }
}
