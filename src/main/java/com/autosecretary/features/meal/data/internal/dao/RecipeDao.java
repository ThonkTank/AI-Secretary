package com.autosecretary.features.meal.data.internal.dao;

import com.autosecretary.features.meal.data.internal.MealCollections;
import com.autosecretary.features.meal.data.internal.mapper.RecipeRowMapper;
import com.autosecretary.features.meal.data.internal.storage.MealStorage;
import com.autosecretary.features.meal.domain.Recipe;

public class RecipeDao extends BaseCollectionDao<Recipe> {
    public RecipeDao(MealStorage storage) {
        super(MealCollections.RECIPES, storage, new RecipeRowMapper(), recipe -> recipe.id);
    }
}
