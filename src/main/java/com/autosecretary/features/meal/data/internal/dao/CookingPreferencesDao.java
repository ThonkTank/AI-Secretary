package com.autosecretary.features.meal.data.internal.dao;

import com.autosecretary.features.meal.data.internal.MealCollections;
import com.autosecretary.features.meal.data.internal.mapper.CookingPreferencesRowMapper;
import com.autosecretary.features.meal.data.internal.storage.MealStorage;
import com.autosecretary.features.meal.domain.CookingPreferences;

public class CookingPreferencesDao extends BaseCollectionDao<CookingPreferences> {
    public CookingPreferencesDao(MealStorage storage) {
        super(MealCollections.COOKING_PREFERENCES, storage, new CookingPreferencesRowMapper(), preferences -> preferences.id, (preferences, id) -> preferences.id = id);
    }
}
