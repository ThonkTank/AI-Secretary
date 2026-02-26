package com.autosecretary.features.meal.domain;

import java.util.List;

public interface PantryRepository {
    List<PantryItem> getPantryItems();
    PantryItem findPantryItemById(long pantryItemId);
    void savePantryItem(PantryItem pantryItem);
    void deletePantryItem(long pantryItemId);

    List<ShoppingListItem> getShoppingListItems(String periodKey);
    void saveShoppingListItem(ShoppingListItem shoppingListItem);
    void deleteShoppingListItem(long shoppingListItemId);
}
