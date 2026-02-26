package com.autosecretary.features.meal.data.internal.repository;

import com.autosecretary.features.meal.data.internal.dao.PantryItemDao;
import com.autosecretary.features.meal.data.internal.dao.ShoppingListDao;
import com.autosecretary.features.meal.data.internal.storage.MealStorage;
import com.autosecretary.features.meal.domain.PantryItem;
import com.autosecretary.features.meal.domain.PantryRepository;
import com.autosecretary.features.meal.domain.ShoppingListItem;

import java.util.List;

public class StoragePantryRepository implements PantryRepository {

    private final PantryItemDao pantryItemDao;
    private final ShoppingListDao shoppingListDao;

    public StoragePantryRepository(MealStorage storage) {
        this.pantryItemDao = new PantryItemDao(storage);
        this.shoppingListDao = new ShoppingListDao(storage);
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
        long id = pantryItemDao.save(pantryItem);
        if (pantryItem.id == null) {
            pantryItem.id = id;
        }
    }

    @Override
    public void deletePantryItem(long pantryItemId) {
        pantryItemDao.deleteById(pantryItemId);
    }

    @Override
    public List<ShoppingListItem> getShoppingListItems(String periodKey) {
        return shoppingListDao.findByPeriodKey(periodKey);
    }

    @Override
    public void saveShoppingListItem(ShoppingListItem shoppingListItem) {
        long id = shoppingListDao.save(shoppingListItem);
        if (shoppingListItem.id == null) {
            shoppingListItem.id = id;
        }
    }

    @Override
    public void deleteShoppingListItem(long shoppingListItemId) {
        shoppingListDao.deleteById(shoppingListItemId);
    }
}
