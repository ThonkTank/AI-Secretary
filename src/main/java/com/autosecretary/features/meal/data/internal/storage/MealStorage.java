package com.autosecretary.features.meal.data.internal.storage;

import java.util.List;
import java.util.Map;

public interface MealStorage {
    Map<String, Object> findById(String collection, long id);
    List<Map<String, Object>> findAll(String collection);
    List<Map<String, Object>> findByField(String collection, String field, Object value);
    long upsert(String collection, Long id, Map<String, Object> row);
    void delete(String collection, long id);
}
