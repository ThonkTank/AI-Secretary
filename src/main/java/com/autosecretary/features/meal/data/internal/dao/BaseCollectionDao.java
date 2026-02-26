package com.autosecretary.features.meal.data.internal.dao;

import com.autosecretary.features.meal.data.internal.mapper.RowMapper;
import com.autosecretary.features.meal.data.internal.storage.MealStorage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class BaseCollectionDao<T> implements CollectionDao<T> {

    private final String collection;
    private final MealStorage storage;
    private final RowMapper<T> mapper;
    private final Function<T, Long> idAccessor;

    public BaseCollectionDao(String collection,
                             MealStorage storage,
                             RowMapper<T> mapper,
                             Function<T, Long> idAccessor) {
        this.collection = collection;
        this.storage = storage;
        this.mapper = mapper;
        this.idAccessor = idAccessor;
    }

    @Override
    public T findById(long id) {
        Map<String, Object> row = storage.findById(collection, id);
        return row == null ? null : mapper.fromRow(row);
    }

    @Override
    public List<T> findAll() {
        List<T> result = new ArrayList<>();
        for (Map<String, Object> row : storage.findAll(collection)) {
            result.add(mapper.fromRow(row));
        }
        return result;
    }

    @Override
    public long save(T value) {
        Long id = idAccessor.apply(value);
        long storedId = storage.upsert(collection, id, mapper.toRow(value));
        return storedId;
    }

    @Override
    public void deleteById(long id) {
        storage.delete(collection, id);
    }
}
