package com.autosecretary.features.meal.data.internal.mapper;

import com.autosecretary.features.meal.data.mapper.LegacyMealFieldKeys;
import com.autosecretary.features.meal.domain.ConsumptionLog;

import java.util.HashMap;
import java.util.Map;

public class ConsumptionLogRowMapper implements RowMapper<ConsumptionLog> {
    @Override
    public ConsumptionLog fromRow(Map<String, Object> row) {
        ConsumptionLog log = new ConsumptionLog();
        log.id = MapperSupport.asNullableLong(MapperSupport.get(row, LegacyMealFieldKeys.Consumption.ID, null));
        log.date = MapperSupport.asLocalDate(MapperSupport.get(row, LegacyMealFieldKeys.Consumption.DATE, "date"));
        log.itemId = MapperSupport.asLong(MapperSupport.get(row, LegacyMealFieldKeys.Consumption.ITEM_ID, "itemId"), 0L);
        log.memberId = MapperSupport.asLong(MapperSupport.get(row, LegacyMealFieldKeys.Consumption.MEMBER_ID, "memberId"), 0L);
        log.recipeId = MapperSupport.asLong(MapperSupport.get(row, LegacyMealFieldKeys.Consumption.RECIPE_ID, "recipeId"), 0L);
        log.servingsConsumed = MapperSupport.asDouble(MapperSupport.get(row, LegacyMealFieldKeys.Consumption.SERVINGS_CONSUMED, "servingsConsumed"), 0);
        log.calories = MapperSupport.asInt(MapperSupport.get(row, LegacyMealFieldKeys.Consumption.CALORIES, "calories"), 0);
        log.protein = MapperSupport.asInt(MapperSupport.get(row, LegacyMealFieldKeys.Consumption.PROTEIN, "protein"), 0);
        log.carbs = MapperSupport.asInt(MapperSupport.get(row, LegacyMealFieldKeys.Consumption.CARBS, "carbs"), 0);
        log.fat = MapperSupport.asInt(MapperSupport.get(row, LegacyMealFieldKeys.Consumption.FAT, "fat"), 0);
        return log;
    }

    @Override
    public Map<String, Object> toRow(ConsumptionLog log) {
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
