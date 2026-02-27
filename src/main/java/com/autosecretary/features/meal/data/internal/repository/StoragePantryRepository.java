package com.autosecretary.features.meal.data.internal.repository;

import com.autosecretary.features.meal.data.internal.MealCollections;
import com.autosecretary.features.meal.data.internal.dao.BaseCollectionDao;
import com.autosecretary.features.meal.data.internal.mapper.PantryItemRowMapper;
import com.autosecretary.features.meal.data.internal.mapper.MealFieldKeys;
import com.autosecretary.features.meal.data.internal.mapper.ShoppingListItemRowMapper;
import com.autosecretary.features.meal.data.internal.storage.MealStorage;
import com.autosecretary.features.meal.domain.PantryItem;
import com.autosecretary.features.meal.domain.PantryRepository;
import com.autosecretary.features.meal.domain.ShoppingListItem;

import java.util.List;

public class StoragePantryRepository implements PantryRepository {

    private final BaseCollectionDao<PantryItem> pantryItemDao;
    private final BaseCollectionDao<ShoppingListItem> shoppingListDao;

    public StoragePantryRepository(MealStorage storage) {
        this.pantryItemDao = new BaseCollectionDao<>(MealCollections.PANTRY_ITEMS, storage, new PantryItemRowMapper(), p -> p.id, (p, id) -> p.id = id);
        this.shoppingListDao = new BaseCollectionDao<>(MealCollections.SHOPPING_LIST_ITEMS, storage, new ShoppingListItemRowMapper(), item -> item.id, (item, id) -> item.id = id);
    }

    @Override
    public List<PantryItem> getPantryItems() {
        return pantryItemDao.findAll();
    }

    @Override
    public PantryItem findPantryItemById(long pantryItemId) {
        return pantryItemDao.findById(pantryItemId);
    }

    @Override
    public void savePantryItem(PantryItem pantryItem) {
        pantryItemDao.save(pantryItem);
    }

    @Override
    public void deletePantryItem(long pantryItemId) {
        pantryItemDao.deleteById(pantryItemId);
    }

    @Override
    public List<ShoppingListItem> getShoppingListItems(String periodKey) {
        return shoppingListDao.findAllByField(MealFieldKeys.PERIOD_KEY, periodKey);
    }

    @Override
    public void saveShoppingListItem(ShoppingListItem shoppingListItem) {
        shoppingListDao.save(shoppingListItem);
    }

    @Override
    public void deleteShoppingListItem(long shoppingListItemId) {
        shoppingListDao.deleteById(shoppingListItemId);
    }
}
