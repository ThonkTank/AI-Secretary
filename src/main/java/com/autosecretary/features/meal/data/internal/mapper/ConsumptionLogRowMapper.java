package com.autosecretary.features.meal.data.internal.mapper;

import com.autosecretary.features.meal.domain.ConsumptionLog;

import java.util.HashMap;
import java.util.Map;

public class ConsumptionLogRowMapper implements RowMapper<ConsumptionLog> {
    @Override
    public ConsumptionLog fromRow(Map<String, Object> row) {
        ConsumptionLog log = new ConsumptionLog();
        log.id = MapperSupport.asNullableLong(row.get(MealFieldKeys.ConsumptionLog.ID));
        log.date = MapperSupport.asLocalDate(row.get(MealFieldKeys.ConsumptionLog.DATE));
        log.itemId = MapperSupport.asLong(row.get(MealFieldKeys.ConsumptionLog.ITEM_ID));
        log.memberId = MapperSupport.asLong(row.get(MealFieldKeys.ConsumptionLog.MEMBER_ID));
        log.recipeId = MapperSupport.asLong(row.get(MealFieldKeys.ConsumptionLog.RECIPE_ID));
        log.servingsConsumed = MapperSupport.asDouble(row.get(MealFieldKeys.ConsumptionLog.SERVINGS_CONSUMED));
        log.calories = MapperSupport.asInt(row.get(MealFieldKeys.ConsumptionLog.CALORIES));
        log.protein = MapperSupport.asInt(row.get(MealFieldKeys.ConsumptionLog.PROTEIN));
        log.carbs = MapperSupport.asInt(row.get(MealFieldKeys.ConsumptionLog.CARBS));
        log.fat = MapperSupport.asInt(row.get(MealFieldKeys.ConsumptionLog.FAT));
        return log;
    }

    @Override
    public Map<String, Object> toRow(ConsumptionLog log) {
        Map<String, Object> row = new HashMap<>();
        row.put(MealFieldKeys.ConsumptionLog.ID, log.id);
        row.put(MealFieldKeys.ConsumptionLog.DATE, MapperSupport.toDateString(log.date));
        row.put(MealFieldKeys.ConsumptionLog.ITEM_ID, log.itemId);
        row.put(MealFieldKeys.ConsumptionLog.MEMBER_ID, log.memberId);
        row.put(MealFieldKeys.ConsumptionLog.RECIPE_ID, log.recipeId);
        row.put(MealFieldKeys.ConsumptionLog.SERVINGS_CONSUMED, log.servingsConsumed);
        row.put(MealFieldKeys.ConsumptionLog.CALORIES, log.calories);
        row.put(MealFieldKeys.ConsumptionLog.PROTEIN, log.protein);
        row.put(MealFieldKeys.ConsumptionLog.CARBS, log.carbs);
        row.put(MealFieldKeys.ConsumptionLog.FAT, log.fat);
        return row;
    }
}
