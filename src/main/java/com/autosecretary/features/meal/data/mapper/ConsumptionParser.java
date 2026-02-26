package com.autosecretary.features.meal.data.mapper;

import com.autosecretary.features.meal.domain.ConsumptionLog;

import java.util.Map;

/**
 * Kompatibilitäts-Fassade, delegiert auf ConsumptionStorageMapper.
 */
public final class ConsumptionParser {
    private ConsumptionParser() {
    }

    public static ConsumptionLog parse(Map<String, Object> row) {
        return ConsumptionStorageMapper.fromStorageRow(row);
    }

    public static Map<String, Object> serialize(ConsumptionLog log) {
        return ConsumptionStorageMapper.toStorageRow(log);
    }
}
