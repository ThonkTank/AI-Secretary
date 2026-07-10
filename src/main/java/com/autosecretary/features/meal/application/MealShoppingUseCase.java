package com.autosecretary.features.meal.application;

import com.autosecretary.features.meal.domain.PantryItem;
import com.autosecretary.features.meal.domain.PantryRepository;
import com.autosecretary.features.meal.domain.ShelfLifeService;
import com.autosecretary.features.meal.domain.ShoppingItemStatus;
import com.autosecretary.features.meal.domain.ShoppingListItem;

import java.time.LocalDate;

public final class MealShoppingUseCase {
    private final PantryRepository pantryRepository;

    public MealShoppingUseCase(PantryRepository pantryRepository) {
        this.pantryRepository = pantryRepository;
    }

    public void updateShoppingItemStatus(String shoppingItemId, ShoppingItemStatus status) {
        pantryRepository.updateShoppingItemStatus(shoppingItemId, status);
    }

    public void createShoppingItemFromNeed(String ingredientName, double neededAmount, String unit) {
        ShoppingListItem item = new ShoppingListItem.Builder(null, ingredientName, Math.max(0.1, neededAmount), unit)
                .periodKey(LocalDate.now().toString())
                .build();
        pantryRepository.saveShoppingListItem(item);
    }

    public void createPantryItem(String ingredientName,
                                 double amount,
                                 String unit,
                                 PantryItem.StorageLocation location,
                                 int shelfLifeDays) {
        LocalDate purchaseDate = LocalDate.now();
        LocalDate expiryDate = ShelfLifeService.calculateExpiryDate(purchaseDate, shelfLifeDays);
        PantryItem item = new PantryItem.Builder(null, ingredientName, Math.max(0.1, amount), unit)
                .purchaseDate(purchaseDate)
                .expiryDate(expiryDate)
                .location(location)
                .build();
        pantryRepository.savePantryItem(item);
    }
}
