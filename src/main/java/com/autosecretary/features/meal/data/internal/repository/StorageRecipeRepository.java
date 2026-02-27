package com.autosecretary.features.meal.data.internal.repository;

import com.autosecretary.features.meal.data.internal.BaseCollectionDao;
import com.autosecretary.features.meal.data.internal.MealCollections;
import com.autosecretary.features.meal.data.internal.mapper.IngredientRowMapper;
import com.autosecretary.features.meal.data.internal.mapper.RecipeRowMapper;
import com.autosecretary.features.meal.data.internal.storage.MealStorage;
import com.autosecretary.features.meal.domain.Ingredient;
import com.autosecretary.features.meal.domain.Recipe;
import com.autosecretary.features.meal.domain.RecipeRepository;

import java.util.List;

public class StorageRecipeRepository implements RecipeRepository {

    private final BaseCollectionDao<Recipe> recipeDao;
    private final BaseCollectionDao<Ingredient> ingredientDao;

    public StorageRecipeRepository(MealStorage storage) {
        this.recipeDao = new BaseCollectionDao<>(MealCollections.RECIPES, storage, new RecipeRowMapper(), r -> r.id, (r, id) -> r.id = id);
        this.ingredientDao = new BaseCollectionDao<>(MealCollections.INGREDIENTS, storage, new IngredientRowMapper(), i -> i.id, (i, id) -> i.id = id);
    }

    @Override
    public List<Recipe> getRecipes() {
        return recipeDao.findAll();
    }

    @Override
    public Recipe findRecipeById(long recipeId) {
        return recipeDao.findById(recipeId);
    }

    @Override
    public void saveRecipe(Recipe recipe) {
        recipeDao.save(recipe);
    }

    @Override
    public void deleteRecipe(long recipeId) {
        recipeDao.deleteById(recipeId);
    }

    @Override
    public List<Ingredient> getIngredients() {
        return ingredientDao.findAll();
    }

    @Override
    public Ingredient findIngredientById(long ingredientId) {
        return ingredientDao.findById(ingredientId);
    }

    @Override
    public void saveIngredient(Ingredient ingredient) {
        ingredientDao.save(ingredient);
    }

    @Override
    public void deleteIngredient(long ingredientId) {
        ingredientDao.deleteById(ingredientId);
    }
}
