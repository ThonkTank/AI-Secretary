package com.autosecretary.features.meal.data.internal.storage;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

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
        return getRowsOrEmpty(collection).values().stream()
                .map(HashMap::new)
                .collect(Collectors.toList());
    }

    @Override
    public List<Map<String, Object>> findByField(String collection, String field, Object value) {
        return getRowsOrEmpty(collection).values().stream()
                .filter(row -> Objects.equals(value, row.get(field)))
                .map(HashMap::new)
                .collect(Collectors.toList());
    }

    @Override
    public long upsert(String collection, Long id, Map<String, Object> row) {
        Map<Long, Map<String, Object>> rows = collections.computeIfAbsent(collection, key -> new LinkedHashMap<>());
        long targetId = getOrGenerateId(collection, id);
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
        return counters.merge(collection, 1L, Long::sum);
    }

    private long getOrGenerateId(String collection, Long explicitId) {
        if (explicitId != null) {
            counters.put(collection, Math.max(counters.getOrDefault(collection, 0L), explicitId));
            return explicitId;
        }
        return nextId(collection);
    }

    private Map<Long, Map<String, Object>> getRowsOrEmpty(String collection) {
        return collections.getOrDefault(collection, Collections.emptyMap());
    }
}
