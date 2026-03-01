package com.autosecretary.features.meal.data.internal.storage;

import com.autosecretary.features.meal.data.internal.MealFieldKeys;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * In-memory implementation of {@link MealStorage}.
 *
 * <p><strong>Data structure:</strong>
 * <ul>
 *   <li>{@code collections}: maps collection names (String) to row maps
 *   <li>Row maps: map row ids (Long) to row data (Map<String, Object>)
 *   <li>{@code counters}: maps collection names to the next auto-generated id
 * </ul>
 *
 * <p><strong>Copy-on-read:</strong> All read operations ({@code findById}, {@code findAll},
 * {@code findByField}) return defensive copies of the stored rows. This prevents external code
 * from accidentally mutating the internal state. Mutations happen only via {@code upsert} and
 * {@code delete}, ensuring the storage layer is the exclusive mutator.
 *
 * <p><strong>Data volatility:</strong> All data stored here is lost when the Android process
 * dies (app killed, device restarted, etc.). Unlike the task and budget features (backed by Room),
 * the meal feature deliberately uses in-memory storage. Data must be re-populated on each app
 * launch — typically via {@code LegacyMealImportService} or the demo seed in
 * {@code MealPlannerPresenter}.
 *
 * <p><strong>Thread safety:</strong> This class is not thread-safe. The maps are not
 * synchronized. Callers must ensure single-threaded access or use external synchronization.
 * In the current architecture, all storage access is routed through a single-threaded executor.
 */
public class InMemoryMealStorage implements MealStorage {

    private final Map<String, Map<Long, Map<String, Object>>> collections = new HashMap<>();
    private final Map<String, Long> counters = new HashMap<>();

    @Override
    public Map<String, Object> findById(String collection, long id) {
        Map<String, Object> row = getRowsOrEmpty(collection).get(id);
        return row == null ? null : defensiveCopy(row);
    }

    @Override
    public List<Map<String, Object>> findAll(String collection) {
        return getRowsOrEmpty(collection).values().stream()
                .map(this::defensiveCopy)
                .toList();
    }

    @Override
    public List<Map<String, Object>> findByField(String collection, String field, Object value) {
        return getRowsOrEmpty(collection).values().stream()
                .filter(row -> Objects.equals(value, row.get(field)))
                .map(this::defensiveCopy)
                .toList();
    }

    @Override
    public long upsert(String collection, Long id, Map<String, Object> row) {
        Objects.requireNonNull(row, "row must not be null");
        Map<Long, Map<String, Object>> rows = collections.computeIfAbsent(collection, key -> new LinkedHashMap<>());
        long targetId = getOrGenerateId(collection, id);
        Map<String, Object> copy = defensiveCopy(row);
        // Defensive: always inject the canonical id into the stored row, regardless of what
        // the caller's map contained. This ensures the stored row's id is never stale or null.
        copy.put(MealFieldKeys.ROW_ID, targetId);
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

    private Map<String, Object> defensiveCopy(Map<String, Object> row) {
        return new HashMap<>(row);
    }

    private long getOrGenerateId(String collection, Long explicitId) {
        if (explicitId != null) {
            // Bump the counter to ensure future auto-generated ids never collide with any
            // explicitly assigned id. Without this, an explicit id larger than the current
            // counter would cause future auto-generated ids to collide with existing rows.
            counters.merge(collection, explicitId, Math::max);
            return explicitId;
        }
        return counters.merge(collection, 1L, Long::sum);
    }

    private Map<Long, Map<String, Object>> getRowsOrEmpty(String collection) {
        return collections.getOrDefault(collection, Collections.emptyMap());
    }
}
