package com.autosecretary.features.meal.data.internal.repository;

import com.autosecretary.features.meal.data.internal.BaseCollectionDao;
import com.autosecretary.features.meal.data.internal.MealCollections;
import com.autosecretary.features.meal.data.internal.mapper.PantryItemRowMapper;
import com.autosecretary.features.meal.data.internal.MealFieldKeys;
import com.autosecretary.features.meal.data.internal.mapper.ShoppingListItemRowMapper;
import com.autosecretary.features.meal.data.internal.storage.MealStorage;
import com.autosecretary.features.meal.domain.PantryItem;
import com.autosecretary.features.meal.domain.PantryRepository;
import com.autosecretary.features.meal.domain.ShoppingListItem;

import java.util.List;

/**
 * Storage-backed implementation of {@link PantryRepository}.
 * <p>
 * Manages two domain models: {@link PantryItem} (pantry inventory) and {@link ShoppingListItem} (shopping list).
 * Each has its own {@link BaseCollectionDao} that handles serialization/deserialization via {@code RowMapper}
 * and adapts the untyped {@link MealStorage} API to typed domain operations.
 */
public class StoragePantryRepository implements PantryRepository {

    private final BaseCollectionDao<PantryItem> pantryItemDao;
    private final BaseCollectionDao<ShoppingListItem> shoppingListDao;

    public StoragePantryRepository(MealStorage storage) {
        // Each BaseCollectionDao is initialized with id accessor and setter lambdas.
        // The accessor reads the entity's id during upsert; the setter injects generated ids back.
        // See StorageMealRepository for detailed explanation of the BaseCollectionDao pattern.
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

    /**
     * Returns all shopping list items for the given period.
     *
     * @param periodKey ISO-8601 date string identifying the period, produced by
     *                  {@link java.time.LocalDate#toString()} (e.g. {@code "2026-02-28"}).
     *                  Must match the value stored in {@link com.autosecretary.features.meal.domain.ShoppingListItem#periodKey}.
     * @return items for this period; empty list if none exist
     */
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
