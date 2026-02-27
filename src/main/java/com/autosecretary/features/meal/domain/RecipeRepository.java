package com.autosecretary.features.meal.domain;

import java.util.List;

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
