package com.autosecretary.features.meal.data.internal.dao;

import com.autosecretary.features.meal.data.internal.MealCollections;
import com.autosecretary.features.meal.data.internal.mapper.ConsumptionLogRowMapper;
import com.autosecretary.features.meal.data.internal.storage.MealStorage;
import com.autosecretary.features.meal.domain.ConsumptionLog;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ConsumptionLogDao extends BaseCollectionDao<ConsumptionLog> {
    private final MealStorage storage;

    public ConsumptionLogDao(MealStorage storage) {
        super(MealCollections.CONSUMPTION_LOGS, storage, new ConsumptionLogRowMapper(), log -> log.id);
        this.storage = storage;
    }

    public List<ConsumptionLog> findInRange(LocalDate fromInclusive, LocalDate toInclusive) {
        List<ConsumptionLog> logs = new ArrayList<>();
        ConsumptionLogRowMapper mapper = new ConsumptionLogRowMapper();
        for (Map<String, Object> row : storage.findAll(MealCollections.CONSUMPTION_LOGS)) {
            ConsumptionLog log = mapper.fromRow(row);
            if (log.date != null && !log.date.isBefore(fromInclusive) && !log.date.isAfter(toInclusive)) {
                logs.add(log);
            }
        }
        return logs;
    }
}
