package com.autosecretary.features.meal.data.internal.storage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class InMemoryMealStorage implements MealStorage {

    private final Map<String, Map<Long, Map<String, Object>>> collections = new HashMap<>();
    private final Map<String, Long> counters = new HashMap<>();

    @Override
    public Map<String, Object> findById(String collection, long id) {
        Map<String, Object> row = getRowsOrEmpty(collection).get(id);
        return row == null ? null : new HashMap<>(row);
    }

    @Override
    public List<Map<String, Object>> findAll(String collection) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : getRowsOrEmpty(collection).values()) {
            result.add(new HashMap<>(row));
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> findByField(String collection, String field, Object value) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : getRowsOrEmpty(collection).values()) {
            Object candidate = row.get(field);
            if (value == null ? candidate == null : value.equals(candidate)) {
                result.add(new HashMap<>(row));
            }
        }
        return result;
    }

    @Override
    public long upsert(String collection, Long id, Map<String, Object> row) {
        Map<Long, Map<String, Object>> rows = collections.computeIfAbsent(collection, key -> new LinkedHashMap<>());
        long targetId;
        if (id != null) {
            targetId = id;
            counters.put(collection, Math.max(counters.getOrDefault(collection, 0L), targetId));
        } else {
            targetId = nextId(collection);
        }
        Map<String, Object> copy = new HashMap<>(row);
        copy.put("id", targetId);
        rows.put(targetId, copy);
        return targetId;
    }

    @Override
    public void delete(String collection, long id) {
        Map<Long, Map<String, Object>> rows = collections.get(collection);
        if (rows != null) {
            rows.remove(id);
        }
    }

    private long nextId(String collection) {
        long value = counters.getOrDefault(collection, 0L) + 1L;
        counters.put(collection, value);
        return value;
    }

    private Map<Long, Map<String, Object>> getRowsOrEmpty(String collection) {
        Map<Long, Map<String, Object>> rows = collections.get(collection);
        return rows != null ? rows : Collections.emptyMap();
    }
}
