package com.autosecretary.features.meal.data.internal.mapper;

import com.autosecretary.features.meal.domain.ShoppingListItem;

import java.util.HashMap;
import java.util.Map;

public class ShoppingListItemRowMapper implements RowMapper<ShoppingListItem> {
    @Override
    public Map<String, Object> toRow(ShoppingListItem item) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", item.id);
        row.put("ingredientId", item.ingredientId);
        row.put("ingredientName", item.ingredientName);
        row.put("amount", item.amount);
        row.put("neededAmount", item.neededAmount);
        row.put("excessAmount", item.excessAmount);
        row.put("unit", item.unit);
        row.put("foodGroupLabel", item.foodGroupLabel);
        row.put("suggestedStore", item.suggestedStore);
        row.put("isPurchased", item.isPurchased);
        row.put("periodKey", item.periodKey);
        row.put("estimatedPriceCents", item.estimatedPriceCents);
        return row;
    }

    @Override
    public ShoppingListItem fromRow(Map<String, Object> row) {
        ShoppingListItem item = new ShoppingListItem();
        item.id = MapperSupport.asNullableLong(row.get("id"));
        item.ingredientId = MapperSupport.asLong(row.get("ingredientId"));
        item.ingredientName = (String) row.get("ingredientName");
        item.amount = MapperSupport.asDouble(row.get("amount"));
        item.neededAmount = MapperSupport.asDouble(row.get("neededAmount"));
        item.excessAmount = MapperSupport.asDouble(row.get("excessAmount"));
        item.unit = (String) row.get("unit");
        item.foodGroupLabel = (String) row.get("foodGroupLabel");
        item.suggestedStore = (String) row.get("suggestedStore");
        item.isPurchased = MapperSupport.asBoolean(row.get("isPurchased"));
        item.periodKey = (String) row.get("periodKey");
        item.estimatedPriceCents = MapperSupport.asInt(row.get("estimatedPriceCents"));
        return item;
    }
}
