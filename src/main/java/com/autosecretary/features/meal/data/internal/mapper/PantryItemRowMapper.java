package com.autosecretary.features.meal.data.internal.mapper;

import com.autosecretary.features.meal.domain.PantryItem;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

public class PantryItemRowMapper implements RowMapper<PantryItem> {
    @Override
    public Map<String, Object> toRow(PantryItem pantryItem) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", pantryItem.id);
        row.put("ingredientId", pantryItem.ingredientId);
        row.put("ingredientName", pantryItem.ingredientName);
        row.put("amount", pantryItem.amount);
        row.put("unit", pantryItem.unit);
        row.put("purchaseDate", pantryItem.purchaseDate == null ? null : pantryItem.purchaseDate.toString());
        row.put("expiryDate", pantryItem.expiryDate == null ? null : pantryItem.expiryDate.toString());
        row.put("location", pantryItem.location == null ? null : pantryItem.location.name());
        return row;
    }

    @Override
    public PantryItem fromRow(Map<String, Object> row) {
        PantryItem pantryItem = new PantryItem();
        pantryItem.id = MapperSupport.asNullableLong(row.get("id"));
        pantryItem.ingredientId = MapperSupport.asLong(row.get("ingredientId"));
        pantryItem.ingredientName = (String) row.get("ingredientName");
        pantryItem.amount = MapperSupport.asDouble(row.get("amount"));
        pantryItem.unit = (String) row.get("unit");
        String purchaseDate = (String) row.get("purchaseDate");
        pantryItem.purchaseDate = purchaseDate == null ? null : LocalDate.parse(purchaseDate);
        String expiryDate = (String) row.get("expiryDate");
        pantryItem.expiryDate = expiryDate == null ? null : LocalDate.parse(expiryDate);
        pantryItem.location = MapperSupport.asEnum(PantryItem.StorageLocation.class, row.get("location"), null);
        return pantryItem;
    }
}
