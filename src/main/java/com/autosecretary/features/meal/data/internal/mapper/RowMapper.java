package com.autosecretary.features.meal.data.internal.mapper;

import java.util.Map;

public interface RowMapper<T> {
    Map<String, Object> toRow(T value);
    T fromRow(Map<String, Object> row);
}
