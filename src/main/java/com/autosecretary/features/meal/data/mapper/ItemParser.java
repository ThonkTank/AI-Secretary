package com.autosecretary.features.meal.data.mapper;

import java.util.HashMap;
import java.util.Map;

/**
 * Kompatibilitäts-Fassade, delegiert auf ItemMealFieldsMapper.
 */
public final class ItemParser {
    private ItemParser() {
    }

    public static ItemMealFieldsMapper.ItemMealFields parse(Map<String, Object> row) {
        return ItemMealFieldsMapper.fromStorageRow(row);
    }

    public static Map<String, Object> serialize(ItemMealFieldsMapper.ItemMealFields fields) {
        Map<String, Object> row = new HashMap<>();
        ItemMealFieldsMapper.toStorageRow(fields, row);
        return row;
    }

    public static void serialize(ItemMealFieldsMapper.ItemMealFields fields, Map<String, Object> targetRow) {
        ItemMealFieldsMapper.toStorageRow(fields, targetRow);
    }
}
