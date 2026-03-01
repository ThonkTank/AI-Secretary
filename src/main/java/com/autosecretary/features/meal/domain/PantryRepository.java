package com.autosecretary.features.meal.domain;

import java.util.List;

/**
 * Domain repository for pantry inventory and shopping list items.
 *
 * <p><strong>Thread safety:</strong> All implementations run on the app's single shared
 * {@code ExecutorService} (wired in {@code AppCompositionRoot}). Do not call these methods
 * on the Android main thread.</p>
 *
 * <p><strong>Return value conventions:</strong> List-returning methods return an empty list
 * (never null) when no data is found. {@link #findPantryItemById} returns null when no
 * matching record exists.</p>
 *
 * <p><strong>periodKey</strong> in {@link #getShoppingListItems} is an ISO date string
 * (e.g. {@code "2026-02-14"}) representing the shopping day. Pass the same key used when
 * saving items to retrieve only the items for that day.</p>
 *
 * <p>The implementation lives in {@code meal/data/internal/repository/StoragePantryRepository}.</p>
 */
public interface PantryRepository {
    List<PantryItem> getPantryItems();
    PantryItem findPantryItemById(String pantryItemId);
    void savePantryItem(PantryItem pantryItem);
    void deletePantryItem(String pantryItemId);

    List<ShoppingListItem> getShoppingListItems(String periodKey);
    void saveShoppingListItem(ShoppingListItem shoppingListItem);
    void deleteShoppingListItem(String shoppingListItemId);
}
