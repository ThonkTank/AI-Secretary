package com.autosecretary.features.meal.data.internal;

import com.autosecretary.features.meal.data.internal.mapper.RowMapper;
import com.autosecretary.features.meal.data.internal.storage.MealStorage;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class BaseCollectionDao<T> {

    private final String collection;
    private final MealStorage storage;
    private final RowMapper<T> mapper;
    private final Function<T, Long> idAccessor;
    private final BiConsumer<T, Long> idSetter;

    public BaseCollectionDao(String collection,
                             MealStorage storage,
                             RowMapper<T> mapper,
                             Function<T, Long> idAccessor,
                             BiConsumer<T, Long> idSetter) {
        this.collection = Objects.requireNonNull(collection, "collection cannot be null");
        this.storage = Objects.requireNonNull(storage, "storage cannot be null");
        this.mapper = Objects.requireNonNull(mapper, "mapper cannot be null");
        this.idAccessor = Objects.requireNonNull(idAccessor, "idAccessor cannot be null");
        this.idSetter = Objects.requireNonNull(idSetter, "idSetter cannot be null");
    }

    public T findById(long id) {
        Map<String, Object> row = storage.findById(collection, id);
        if (row == null) {
            return null;
        }
        return mapper.fromRow(row);
    }

    public List<T> findAll() {
        return mapRows(storage.findAll(collection));
    }

    /**
     * Persists the given value to storage.
     *
     * <p>If the value's ID (accessed via idAccessor) is null, the storage layer is expected
     * to generate a new ID. The generated ID is then set back on the value via idSetter.
     * If the ID is already set (non-null), idSetter is not invoked and the existing ID is used.
     *
     * @param value The value to persist. Must not be null. Its ID (if null) will be generated
     *              and set back via idSetter.
     */
    public void save(T value) {
        Long id = idAccessor.apply(value);
        long storedId = storage.upsert(collection, id, mapper.toRow(value));
        if (id == null) {
            idSetter.accept(value, storedId);
        }
    }

    public void deleteById(long id) {
        storage.delete(collection, id);
    }

    public List<T> findAll(Predicate<T> filter) {
        return findAll().stream().filter(filter).collect(Collectors.toList());
    }

    public List<T> findAllByField(String field, Object value) {
        return mapRows(storage.findByField(collection, field, value));
    }

    private List<T> mapRows(List<Map<String, Object>> rows) {
        return rows.stream().map(mapper::fromRow).collect(Collectors.toList());
    }
}
