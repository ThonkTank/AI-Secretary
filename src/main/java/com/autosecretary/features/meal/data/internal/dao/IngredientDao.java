package com.autosecretary.features.meal.data.internal.dao;

import com.autosecretary.features.meal.data.internal.MealCollections;
import com.autosecretary.features.meal.data.internal.mapper.IngredientRowMapper;
import com.autosecretary.features.meal.data.internal.storage.MealStorage;
import com.autosecretary.features.meal.domain.Ingredient;

public class IngredientDao extends BaseCollectionDao<Ingredient> {
    public IngredientDao(MealStorage storage) {
        super(MealCollections.INGREDIENTS, storage, new IngredientRowMapper(), ingredient -> ingredient.id, (ingredient, id) -> ingredient.id = id);
    }
}
