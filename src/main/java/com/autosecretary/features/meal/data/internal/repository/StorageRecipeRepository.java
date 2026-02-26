package com.autosecretary.features.meal.data.internal.repository;

import com.autosecretary.features.meal.data.internal.dao.IngredientDao;
import com.autosecretary.features.meal.data.internal.dao.RecipeDao;
import com.autosecretary.features.meal.data.internal.storage.MealStorage;
import com.autosecretary.features.meal.domain.Ingredient;
import com.autosecretary.features.meal.domain.Recipe;
import com.autosecretary.features.meal.domain.RecipeRepository;

import java.util.List;

public class StorageRecipeRepository implements RecipeRepository {

    private final RecipeDao recipeDao;
    private final IngredientDao ingredientDao;

    public StorageRecipeRepository(MealStorage storage) {
        this.recipeDao = new RecipeDao(storage);
        this.ingredientDao = new IngredientDao(storage);
    }

    @Override
    public List<Recipe> getAllRecipes() {
        return recipeDao.findAll();
    }

    @Override
    public Recipe findRecipeById(long recipeId) {
        return recipeDao.findById(recipeId);
    }

    @Override
    public void saveRecipe(Recipe recipe) {
        long id = recipeDao.save(recipe);
        if (recipe.id == null) {
            recipe.id = id;
        }
    }

    @Override
    public void deleteRecipe(long recipeId) {
        recipeDao.deleteById(recipeId);
    }

    @Override
    public List<Ingredient> getAllIngredients() {
        return ingredientDao.findAll();
    }

    @Override
    public Ingredient findIngredientById(long ingredientId) {
        return ingredientDao.findById(ingredientId);
    }

    @Override
    public void saveIngredient(Ingredient ingredient) {
        long id = ingredientDao.save(ingredient);
        if (ingredient.id == null) {
            ingredient.id = id;
        }
    }

    @Override
    public void deleteIngredient(long ingredientId) {
        ingredientDao.deleteById(ingredientId);
    }
}
