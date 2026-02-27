package com.autosecretary.features.meal.application;

import com.autosecretary.features.meal.application.internal.EntityLookupHelper;
import com.autosecretary.features.meal.domain.Recipe;
import com.autosecretary.features.meal.domain.RecipeRepository;
import com.autosecretary.features.meal.domain.RecipeScalingResult;
import com.autosecretary.features.meal.domain.RecipeScalingService;

/**
 * Application-Use-Case fuer Rezept-Skalierung mit Precision-Regeln.
 */
public class ScaleRecipeUseCase {

    private final RecipeRepository recipeRepository;

    public ScaleRecipeUseCase(RecipeRepository recipeRepository) {
        this.recipeRepository = recipeRepository;
    }

    public RecipeScalingResult execute(long recipeId, double requestedServings) {
        Recipe recipe = EntityLookupHelper.requireFound(
                recipeRepository.findRecipeById(recipeId), "Recipe", recipeId);
        return RecipeScalingService.scaleRecipe(recipe, requestedServings);
    }
}
