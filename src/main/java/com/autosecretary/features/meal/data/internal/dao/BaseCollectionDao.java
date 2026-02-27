package com.autosecretary.features.meal.data.internal.dao;

import com.autosecretary.features.meal.data.internal.mapper.RowMapper;
import com.autosecretary.features.meal.data.internal.storage.MealStorage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;

public class BaseCollectionDao<T> {

    protected final String collection;
    protected final MealStorage storage;
    protected final RowMapper<T> mapper;
    private final Function<T, Long> idAccessor;
    private final BiConsumer<T, Long> idSetter;

    public BaseCollectionDao(String collection,
                             MealStorage storage,
                             RowMapper<T> mapper,
                             Function<T, Long> idAccessor,
                             BiConsumer<T, Long> idSetter) {
        this.collection = collection;
        this.storage = storage;
        this.mapper = mapper;
        this.idAccessor = idAccessor;
        this.idSetter = idSetter;
    }

    public T findById(long id) {
        Map<String, Object> row = storage.findById(collection, id);
        return row == null ? null : mapper.fromRow(row);
    }

    public List<T> findAll() {
        List<T> result = new ArrayList<>();
        for (Map<String, Object> row : storage.findAll(collection)) {
            result.add(mapper.fromRow(row));
        }
        return result;
    }

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
        List<T> result = new ArrayList<>();
        for (Map<String, Object> row : storage.findAll(collection)) {
            T value = mapper.fromRow(row);
            if (filter.test(value)) {
                result.add(value);
            }
        }
        return result;
    }

    public List<T> findAllByField(String field, Object value) {
        List<T> result = new ArrayList<>();
        for (Map<String, Object> row : storage.findByField(collection, field, value)) {
            result.add(mapper.fromRow(row));
        }
        return result;
    }
}
