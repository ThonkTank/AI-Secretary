package com.autosecretary.features.meal.data.internal.mapper;

import com.autosecretary.features.meal.data.mapper.RecipeStorageMapper;
import com.autosecretary.features.meal.domain.Recipe;

import java.util.Map;

public class RecipeRowMapper implements RowMapper<Recipe> {
    @Override
    public Map<String, Object> toRow(Recipe recipe) {
        return RecipeStorageMapper.toStorageRow(recipe);
    }

    @Override
    public Recipe fromRow(Map<String, Object> row) {
        return RecipeStorageMapper.fromStorageRow(row);
    }
}
