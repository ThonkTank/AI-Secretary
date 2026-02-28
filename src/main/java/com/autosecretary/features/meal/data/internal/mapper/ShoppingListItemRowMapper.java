package com.autosecretary.features.meal.data.internal.mapper;

import com.autosecretary.features.meal.data.internal.MealFieldKeys;
import com.autosecretary.features.meal.domain.ShoppingListItem;

import java.util.HashMap;
import java.util.Map;

/**
 * {@link RowMapper} for {@link ShoppingListItem}.
 *
 * <p>The {@code periodKey} field uses the shared top-level {@link MealFieldKeys#PERIOD_KEY}
 * constant (also used by {@link WeeklyFoodTargetRowMapper}). This is the storage key that
 * {@code StoragePantryRepository.getShoppingListItems(periodKey)} queries against to retrieve
 * all items for a given period.
 */
public class ShoppingListItemRowMapper implements RowMapper<ShoppingListItem> {

    @Override
    public Map<String, Object> toRow(ShoppingListItem item) {
        Map<String, Object> row = new HashMap<>();
        row.put(MealFieldKeys.ShoppingListItem.ID, item.id);
        row.put(MealFieldKeys.ShoppingListItem.INGREDIENT_ID, item.ingredientId);
        row.put(MealFieldKeys.ShoppingListItem.INGREDIENT_NAME, item.ingredientName);
        row.put(MealFieldKeys.ShoppingListItem.AMOUNT, item.amount);
        row.put(MealFieldKeys.ShoppingListItem.NEEDED_AMOUNT, item.neededAmount);
        row.put(MealFieldKeys.ShoppingListItem.EXCESS_AMOUNT, item.excessAmount);
        row.put(MealFieldKeys.ShoppingListItem.UNIT, item.unit);
        row.put(MealFieldKeys.ShoppingListItem.FOOD_GROUP_LABEL, item.foodGroupLabel);
        row.put(MealFieldKeys.ShoppingListItem.SUGGESTED_STORE, item.suggestedStore);
        row.put(MealFieldKeys.ShoppingListItem.IS_PURCHASED, item.isPurchased ? 1 : 0);
        row.put(MealFieldKeys.PERIOD_KEY, item.periodKey);
        row.put(MealFieldKeys.ShoppingListItem.ESTIMATED_PRICE_CENTS, item.estimatedPriceCents);
        return row;
    }

    @Override
    public ShoppingListItem fromRow(Map<String, Object> row) {
        ShoppingListItem item = new ShoppingListItem();
        item.id = MapperSupport.asNullableLong(row.get(MealFieldKeys.ShoppingListItem.ID));
        item.ingredientId = MapperSupport.asLong(row.get(MealFieldKeys.ShoppingListItem.INGREDIENT_ID));
        // String fields use raw casts: the storage layer always serializes them as strings via toRow(),
        // so the cast is safe (no type mismatch). If storage changes, wrap this in MapperSupport.asString().
        item.ingredientName = (String) row.get(MealFieldKeys.ShoppingListItem.INGREDIENT_NAME);
        item.amount = MapperSupport.asDouble(row.get(MealFieldKeys.ShoppingListItem.AMOUNT));
        item.neededAmount = MapperSupport.asDouble(row.get(MealFieldKeys.ShoppingListItem.NEEDED_AMOUNT));
        item.excessAmount = MapperSupport.asDouble(row.get(MealFieldKeys.ShoppingListItem.EXCESS_AMOUNT));
        item.unit = (String) row.get(MealFieldKeys.ShoppingListItem.UNIT);
        item.foodGroupLabel = (String) row.get(MealFieldKeys.ShoppingListItem.FOOD_GROUP_LABEL);
        item.suggestedStore = (String) row.get(MealFieldKeys.ShoppingListItem.SUGGESTED_STORE);
        item.isPurchased = MapperSupport.asBoolean(row.get(MealFieldKeys.ShoppingListItem.IS_PURCHASED));
        item.periodKey = (String) row.get(MealFieldKeys.PERIOD_KEY);
        item.estimatedPriceCents = MapperSupport.asInt(row.get(MealFieldKeys.ShoppingListItem.ESTIMATED_PRICE_CENTS));
        return item;
    }
}
