package com.autosecretary.features.meal.data.internal.dao;

import com.autosecretary.features.meal.data.internal.MealCollections;
import com.autosecretary.features.meal.data.internal.mapper.ConsumptionLogRowMapper;
import com.autosecretary.features.meal.data.internal.storage.MealStorage;
import com.autosecretary.features.meal.domain.ConsumptionLog;

import java.time.LocalDate;
import java.util.List;

public class ConsumptionLogDao extends BaseCollectionDao<ConsumptionLog> {

    public ConsumptionLogDao(MealStorage storage) {
        super(MealCollections.CONSUMPTION_LOGS, storage, new ConsumptionLogRowMapper(), log -> log.id, (log, id) -> log.id = id);
    }

    public List<ConsumptionLog> findInRange(LocalDate fromInclusive, LocalDate toInclusive) {
        return findAll(log -> log.date != null && !log.date.isBefore(fromInclusive) && !log.date.isAfter(toInclusive));
    }
}
