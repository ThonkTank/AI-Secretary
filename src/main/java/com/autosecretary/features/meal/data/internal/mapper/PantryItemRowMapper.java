package com.autosecretary.features.meal.data.internal.mapper;

import com.autosecretary.features.meal.data.internal.MealFieldKeys;
import com.autosecretary.features.meal.domain.PantryItem;

import java.util.HashMap;
import java.util.Map;

/**
 * {@link RowMapper} for {@link PantryItem}.
 *
 * <p>Straightforward scalar mapper: dates are serialized to ISO-8601 strings; enum field
 * {@code location} ({@link PantryItem.StorageLocation}) is stored by name. No complex
 * nested objects.
 */
public class PantryItemRowMapper implements RowMapper<PantryItem> {
    @Override
    public Map<String, Object> toRow(PantryItem pantryItem) {
        Map<String, Object> row = new HashMap<>();
        row.put(MealFieldKeys.PantryItem.ID, pantryItem.id);
        row.put(MealFieldKeys.PantryItem.INGREDIENT_ID, pantryItem.ingredientId);
        row.put(MealFieldKeys.PantryItem.INGREDIENT_NAME, pantryItem.ingredientName);
        row.put(MealFieldKeys.PantryItem.AMOUNT, pantryItem.amount);
        row.put(MealFieldKeys.PantryItem.UNIT, pantryItem.unit);
        row.put(MealFieldKeys.PantryItem.PURCHASE_DATE, MapperSupport.toDateString(pantryItem.purchaseDate));
        row.put(MealFieldKeys.PantryItem.EXPIRY_DATE, MapperSupport.toDateString(pantryItem.expiryDate));
        row.put(MealFieldKeys.PantryItem.LOCATION, MapperSupport.enumNameOrNull(pantryItem.location));
        return row;
    }

    @Override
    public PantryItem fromRow(Map<String, Object> row) {
        PantryItem pantryItem = new PantryItem();
        pantryItem.id = MapperSupport.asNullableLong(row.get(MealFieldKeys.PantryItem.ID));
        pantryItem.ingredientId = MapperSupport.asLong(row.get(MealFieldKeys.PantryItem.INGREDIENT_ID));
        pantryItem.ingredientName = (String) row.get(MealFieldKeys.PantryItem.INGREDIENT_NAME);
        pantryItem.amount = MapperSupport.asDouble(row.get(MealFieldKeys.PantryItem.AMOUNT));
        pantryItem.unit = (String) row.get(MealFieldKeys.PantryItem.UNIT);
        pantryItem.purchaseDate = MapperSupport.asLocalDate(row.get(MealFieldKeys.PantryItem.PURCHASE_DATE));
        pantryItem.expiryDate = MapperSupport.asLocalDate(row.get(MealFieldKeys.PantryItem.EXPIRY_DATE));
        pantryItem.location = MapperSupport.asEnum(PantryItem.StorageLocation.class, row.get(MealFieldKeys.PantryItem.LOCATION), null);
        return pantryItem;
    }
}
