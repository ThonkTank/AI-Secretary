package com.autosecretary.features.meal.data.internal.dao;

import com.autosecretary.features.meal.data.internal.MealCollections;
import com.autosecretary.features.meal.data.internal.mapper.PantryItemRowMapper;
import com.autosecretary.features.meal.data.internal.storage.MealStorage;
import com.autosecretary.features.meal.domain.PantryItem;

public class PantryItemDao extends BaseCollectionDao<PantryItem> {
    public PantryItemDao(MealStorage storage) {
        super(MealCollections.PANTRY_ITEMS, storage, new PantryItemRowMapper(), pantryItem -> pantryItem.id, (pantryItem, id) -> pantryItem.id = id);
    }
}
