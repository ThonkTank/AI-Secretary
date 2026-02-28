package com.autosecretary.features.meal.application;

import com.autosecretary.features.meal.application.internal.EntityLookupHelper;
import com.autosecretary.features.meal.domain.Recipe;
import com.autosecretary.features.meal.domain.RecipeRepository;
import com.autosecretary.features.meal.domain.RecipeScalingResult;
import com.autosecretary.features.meal.domain.RecipeScalingService;

/**
 * Scales a recipe to a requested number of servings and returns the adjusted ingredient amounts.
 *
 * <p>Delegates the actual scaling math to {@link com.autosecretary.features.meal.domain.RecipeScalingService},
 * which applies precision rules: for example, a recipe with {@code ScalingPrecision.EXACT} may
 * allow fractional amounts, while {@code ROUGH} snaps to convenient whole numbers.
 *
 * <p>Throws {@link IllegalArgumentException} if no recipe with the given ID exists.
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
