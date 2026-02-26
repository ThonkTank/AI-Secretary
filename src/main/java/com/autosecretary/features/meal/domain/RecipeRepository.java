package com.autosecretary.features.meal.domain;

import java.util.List;

public interface RecipeRepository {
    List<Recipe> getAllRecipes();
    Recipe findRecipeById(long recipeId);
    void saveRecipe(Recipe recipe);
    void deleteRecipe(long recipeId);

    List<Ingredient> getAllIngredients();
    Ingredient findIngredientById(long ingredientId);
    void saveIngredient(Ingredient ingredient);
    void deleteIngredient(long ingredientId);
}
