package com.autosecretary.features.meal.data.mapper;

import com.autosecretary.features.meal.domain.Recipe;

import java.util.Map;

/**
 * Kompatibilitäts-Fassade, delegiert auf RecipeStorageMapper.
 */
public final class RecipeParser {
    private RecipeParser() {
    }

    public static Recipe parse(Map<String, Object> row) {
        return RecipeStorageMapper.fromStorageRow(row);
    }

    public static Map<String, Object> serialize(Recipe recipe) {
        return RecipeStorageMapper.toStorageRow(recipe);
    }
}
