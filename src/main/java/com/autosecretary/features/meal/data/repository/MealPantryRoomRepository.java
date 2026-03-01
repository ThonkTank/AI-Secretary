package com.autosecretary.features.meal.data.repository;

import com.autosecretary.features.meal.data.dao.MealPantryDao;
import com.autosecretary.features.meal.data.entity.MealPantryItemEntity;
import com.autosecretary.features.meal.data.entity.MealShoppingListItemEntity;
import com.autosecretary.features.meal.domain.PantryItem;
import com.autosecretary.features.meal.domain.PantryRepository;
import com.autosecretary.features.meal.domain.ShoppingListItem;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class MealPantryRoomRepository implements PantryRepository {

    private final MealPantryDao pantryDao;

    public MealPantryRoomRepository(MealPantryDao pantryDao) {
        this.pantryDao = pantryDao;
    }

    // ---- PantryItem ----

    @Override
    public List<PantryItem> getPantryItems() {
        return pantryDao.findAll().stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public PantryItem findPantryItemById(String pantryItemId) {
        MealPantryItemEntity e = pantryDao.findById(pantryItemId);
        return e == null ? null : toDomain(e);
    }

    @Override
    public void savePantryItem(PantryItem item) {
        if (item.id == null) item.id = UUID.randomUUID().toString();
        pantryDao.insert(toEntity(item));
    }

    @Override
    public void deletePantryItem(String pantryItemId) {
        pantryDao.deleteById(pantryItemId);
    }

    private PantryItem toDomain(MealPantryItemEntity e) {
        PantryItem p = new PantryItem();
        p.id = e.id;
        p.ingredientId = e.ingredientId;
        p.ingredientName = e.ingredientName;
        p.amount = e.amount;
        p.unit = e.unit;
        p.purchaseDate = e.purchaseDate;
        p.expiryDate = e.expiryDate;
        p.location = e.location;
        return p;
    }

    private MealPantryItemEntity toEntity(PantryItem p) {
        MealPantryItemEntity e = new MealPantryItemEntity();
        e.id = p.id;
        e.ingredientId = p.ingredientId;
        e.ingredientName = p.ingredientName;
        e.amount = p.amount;
        e.unit = p.unit;
        e.purchaseDate = p.purchaseDate;
        e.expiryDate = p.expiryDate;
        e.location = p.location;
        return e;
    }

    // ---- ShoppingListItem ----

    @Override
    public List<ShoppingListItem> getShoppingListItems(String periodKey) {
        return pantryDao.findShoppingItemsByPeriodKey(periodKey).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public void saveShoppingListItem(ShoppingListItem item) {
        if (item.id == null) item.id = UUID.randomUUID().toString();
        pantryDao.insertShoppingItem(toEntity(item));
    }

    @Override
    public void deleteShoppingListItem(String shoppingListItemId) {
        pantryDao.deleteShoppingItemById(shoppingListItemId);
    }

    private ShoppingListItem toDomain(MealShoppingListItemEntity e) {
        ShoppingListItem s = new ShoppingListItem();
        s.id = e.id;
        s.ingredientId = e.ingredientId;
        s.ingredientName = e.ingredientName;
        s.amount = e.amount;
        s.neededAmount = e.neededAmount;
        s.excessAmount = e.excessAmount;
        s.unit = e.unit;
        s.foodGroupLabel = e.foodGroupLabel;
        s.suggestedStore = e.suggestedStore;
        s.isPurchased = e.isPurchased;
        s.periodKey = e.periodKey;
        s.estimatedPriceCents = e.estimatedPriceCents;
        return s;
    }

    private MealShoppingListItemEntity toEntity(ShoppingListItem s) {
        MealShoppingListItemEntity e = new MealShoppingListItemEntity();
        e.id = s.id;
        e.ingredientId = s.ingredientId;
        e.ingredientName = s.ingredientName;
        e.amount = s.amount;
        e.neededAmount = s.neededAmount;
        e.excessAmount = s.excessAmount;
        e.unit = s.unit;
        e.foodGroupLabel = s.foodGroupLabel;
        e.suggestedStore = s.suggestedStore;
        e.isPurchased = s.isPurchased;
        e.periodKey = s.periodKey;
        e.estimatedPriceCents = s.estimatedPriceCents;
        return e;
    }
}
