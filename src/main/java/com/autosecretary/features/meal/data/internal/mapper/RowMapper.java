package com.autosecretary.features.meal.data.internal.mapper;

import java.util.Map;

/**
 * Serialization contract between domain entities and untyped storage rows.
 *
 * <p>A RowMapper converts between a strongly-typed domain entity {@code T} and an untyped
 * {@code Map<String, Object>} row used by the meal storage layer. Each domain model
 * (e.g., {@code Recipe}, {@code Ingredient}, {@code MealPlan}) that needs to be persisted
 * requires a corresponding mapper implementation.
 *
 * <h3>Responsibilities</h3>
 *
 * <p><b>{@code toRow()}</b> — serialization
 * <ul>
 *   <li>Convert domain entity {@code T} to {@code Map<String, Object>}
 *   <li>Field names must match constants in {@link MealFieldKeys}
 *   <li>Use {@link MapperSupport} methods to serialize enums, dates, collections safely
 * </ul>
 *
 * <p><b>{@code fromRow()}</b> — deserialization
 * <ul>
 *   <li>Convert {@code Map<String, Object>} to domain entity {@code T}
 *   <li>Use {@link MapperSupport} methods for safe null-handling and type conversion
 *   <li>Return a fully-constructed, valid instance of {@code T}
 * </ul>
 *
 * <h3>Integration</h3>
 *
 * <p>RowMappers are used by {@link BaseCollectionDao} to persist domain models to
 * {@link MealStorage} (currently in-memory, but abstracted for future database migration).
 * The storage layer is untyped; the RowMapper is responsible for enforcing type safety
 * and validation at the serialization boundary.
 *
 * <h3>How to Implement</h3>
 *
 * <p>See the mapper package README or examine existing mappers like
 * {@code RecipeRowMapper} or {@code IngredientRowMapper} for examples. Use
 * {@link MapperSupport} for safe conversions and follow the established field-naming
 * and serialization patterns.
 *
 * @param <T> the domain entity type
 * @see MapperSupport
 * @see MealFieldKeys
 * @see BaseCollectionDao
 */
public interface RowMapper<T> {
    /**
     * Serialize a domain entity to a storage row.
     *
     * @param value the domain entity to serialize
     * @return an untyped map with field names from {@link MealFieldKeys}
     */
    Map<String, Object> toRow(T value);

    /**
     * Deserialize a storage row to a domain entity.
     *
     * @param row an untyped map with field names from {@link MealFieldKeys}
     * @return a fully-constructed domain entity
     */
    T fromRow(Map<String, Object> row);
}
