package com.autosecretary.features.meal.data.internal.mapper;

import com.autosecretary.features.meal.domain.ConsumptionLog;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

public class ConsumptionLogRowMapper implements RowMapper<ConsumptionLog> {
    @Override
    public Map<String, Object> toRow(ConsumptionLog log) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", log.id);
        row.put("date", log.date == null ? null : log.date.toString());
        row.put("itemId", log.itemId);
        row.put("memberId", log.memberId);
        row.put("recipeId", log.recipeId);
        row.put("servingsConsumed", log.servingsConsumed);
        row.put("calories", log.calories);
        row.put("protein", log.protein);
        row.put("carbs", log.carbs);
        row.put("fat", log.fat);
        return row;
    }

    @Override
    public ConsumptionLog fromRow(Map<String, Object> row) {
        ConsumptionLog log = new ConsumptionLog();
        log.id = MapperSupport.asNullableLong(row.get("id"));
        String date = (String) row.get("date");
        log.date = date == null ? null : LocalDate.parse(date);
        log.itemId = MapperSupport.asLong(row.get("itemId"));
        log.memberId = MapperSupport.asLong(row.get("memberId"));
        log.recipeId = MapperSupport.asLong(row.get("recipeId"));
        log.servingsConsumed = MapperSupport.asDouble(row.get("servingsConsumed"));
        log.calories = MapperSupport.asInt(row.get("calories"));
        log.protein = MapperSupport.asInt(row.get("protein"));
        log.carbs = MapperSupport.asInt(row.get("carbs"));
        log.fat = MapperSupport.asInt(row.get("fat"));
        return log;
    }
}
