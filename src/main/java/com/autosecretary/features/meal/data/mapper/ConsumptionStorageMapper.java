package com.autosecretary.features.meal.data.mapper;

import com.autosecretary.features.meal.domain.ConsumptionLog;

import java.util.HashMap;
import java.util.Map;

public final class ConsumptionStorageMapper {
    private ConsumptionStorageMapper() {
    }

    public static ConsumptionLog fromStorageRow(Map<String, Object> row) {
        ConsumptionLog log = new ConsumptionLog();
        log.id = LegacyMapperSupport.asNullableLong(LegacyMapperSupport.get(row, LegacyMealFieldKeys.Consumption.ID, null));
        log.date = LegacyMapperSupport.asLocalDate(LegacyMapperSupport.get(row, LegacyMealFieldKeys.Consumption.DATE, "date"));
        log.itemId = LegacyMapperSupport.asLong(LegacyMapperSupport.get(row, LegacyMealFieldKeys.Consumption.ITEM_ID, "itemId"), 0L);
        log.memberId = LegacyMapperSupport.asLong(LegacyMapperSupport.get(row, LegacyMealFieldKeys.Consumption.MEMBER_ID, "memberId"), 0L);
        log.recipeId = LegacyMapperSupport.asLong(LegacyMapperSupport.get(row, LegacyMealFieldKeys.Consumption.RECIPE_ID, "recipeId"), 0L);
        log.servingsConsumed = LegacyMapperSupport.asDouble(LegacyMapperSupport.get(row, LegacyMealFieldKeys.Consumption.SERVINGS_CONSUMED, "servingsConsumed"), 0);
        log.calories = LegacyMapperSupport.asInt(LegacyMapperSupport.get(row, LegacyMealFieldKeys.Consumption.CALORIES, "calories"), 0);
        log.protein = LegacyMapperSupport.asInt(LegacyMapperSupport.get(row, LegacyMealFieldKeys.Consumption.PROTEIN, "protein"), 0);
        log.carbs = LegacyMapperSupport.asInt(LegacyMapperSupport.get(row, LegacyMealFieldKeys.Consumption.CARBS, "carbs"), 0);
        log.fat = LegacyMapperSupport.asInt(LegacyMapperSupport.get(row, LegacyMealFieldKeys.Consumption.FAT, "fat"), 0);
        return log;
    }

    public static Map<String, Object> toStorageRow(ConsumptionLog log) {
        Map<String, Object> row = new HashMap<>();
        row.put(LegacyMealFieldKeys.Consumption.ID, log.id);
        row.put(LegacyMealFieldKeys.Consumption.DATE, log.date == null ? null : log.date.toString());
        row.put(LegacyMealFieldKeys.Consumption.ITEM_ID, log.itemId);
        row.put(LegacyMealFieldKeys.Consumption.MEMBER_ID, log.memberId);
        row.put(LegacyMealFieldKeys.Consumption.RECIPE_ID, log.recipeId);
        row.put(LegacyMealFieldKeys.Consumption.SERVINGS_CONSUMED, log.servingsConsumed);
        row.put(LegacyMealFieldKeys.Consumption.CALORIES, log.calories);
        row.put(LegacyMealFieldKeys.Consumption.PROTEIN, log.protein);
        row.put(LegacyMealFieldKeys.Consumption.CARBS, log.carbs);
        row.put(LegacyMealFieldKeys.Consumption.FAT, log.fat);
        return row;
    }
}
