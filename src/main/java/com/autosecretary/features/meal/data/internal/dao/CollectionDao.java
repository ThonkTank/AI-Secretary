package com.autosecretary.features.meal.data.internal.dao;

import java.util.List;

public interface CollectionDao<T> {
    T findById(long id);
    List<T> findAll();
    long save(T value);
    void deleteById(long id);
}
