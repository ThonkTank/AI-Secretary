package com.autosecretary.features.meal.domain;

import java.util.List;

/**
 * Domain repository for recipes and ingredients.
 *
 * <p><strong>Thread safety:</strong> All implementations run on the app's single shared
 * {@code ExecutorService} (wired in {@code AppCompositionRoot}). Do not call these methods
 * on the Android main thread.</p>
 *
 * <p><strong>Return value conventions:</strong> List-returning methods return an empty list
 * (never null) when no data is found. {@link #findRecipeById} and {@link #findIngredientById}
 * return null when no matching record exists.</p>
 *
 * <p>The implementation lives in {@code meal/data/internal/repository/StorageRecipeRepository}.</p>
 */
public interface RecipeRepository {
    List<Recipe> getRecipes();
    Recipe findRecipeById(long recipeId);
    void saveRecipe(Recipe recipe);
    void deleteRecipe(long recipeId);

    List<Ingredient> getIngredients();
    Ingredient findIngredientById(long ingredientId);
    void saveIngredient(Ingredient ingredient);
    void deleteIngredient(long ingredientId);
}
