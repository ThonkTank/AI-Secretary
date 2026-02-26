package com.autosecretary.features.meal.data.internal.mapper;

import com.autosecretary.features.meal.data.mapper.ConsumptionStorageMapper;
import com.autosecretary.features.meal.domain.ConsumptionLog;

import java.util.Map;

public class ConsumptionLogRowMapper implements RowMapper<ConsumptionLog> {
    @Override
    public Map<String, Object> toRow(ConsumptionLog log) {
        return ConsumptionStorageMapper.toStorageRow(log);
    }

    @Override
    public ConsumptionLog fromRow(Map<String, Object> row) {
        return ConsumptionStorageMapper.fromStorageRow(row);
    }
}
