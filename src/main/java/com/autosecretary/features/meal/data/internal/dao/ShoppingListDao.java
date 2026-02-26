package com.autosecretary.features.meal.data.internal.dao;

import com.autosecretary.features.meal.data.internal.MealCollections;
import com.autosecretary.features.meal.data.internal.mapper.ShoppingListItemRowMapper;
import com.autosecretary.features.meal.data.internal.storage.MealStorage;
import com.autosecretary.features.meal.domain.ShoppingListItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ShoppingListDao extends BaseCollectionDao<ShoppingListItem> {
    private final MealStorage storage;

    public ShoppingListDao(MealStorage storage) {
        super(MealCollections.SHOPPING_LIST_ITEMS, storage, new ShoppingListItemRowMapper(), item -> item.id);
        this.storage = storage;
    }

    public List<ShoppingListItem> findByPeriodKey(String periodKey) {
        List<ShoppingListItem> items = new ArrayList<>();
        ShoppingListItemRowMapper mapper = new ShoppingListItemRowMapper();
        for (Map<String, Object> row : storage.findByField(MealCollections.SHOPPING_LIST_ITEMS, "periodKey", periodKey)) {
            items.add(mapper.fromRow(row));
        }
        return items;
    }
}
