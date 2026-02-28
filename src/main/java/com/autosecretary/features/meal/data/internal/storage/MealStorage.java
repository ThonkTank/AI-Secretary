package com.autosecretary.features.meal.data.internal.storage;

import java.util.List;
import java.util.Map;

/**
 * Abstraction for storing and querying untyped row data organized into named collections.
 *
 * Each "row" is a {@code Map<String, Object>} representing a single record with arbitrary
 * string-keyed fields. Rows are stored in named "collections" (e.g., "meals", "pantry_items")
 * and accessed by numeric id or field equality.
 *
 * <p><strong>Note on id parameter types:</strong> {@code findById} takes primitive {@code long}
 * because rows always have an id. {@code upsert} takes {@code Long} (nullable) to allow callers
 * to request auto-generated ids by passing {@code null}.</p>
 *
 * <p><strong>Row mutation:</strong> All read operations return defensive copies. External mutation
 * of the returned map will not affect the stored data. Mutations happen only via {@code upsert}
 * and {@code delete}.</p>
 */
public interface MealStorage {
    /**
     * Retrieves a single row by id from the named collection.
     *
     * @param collection the collection name (e.g., "meals")
     * @param id the row id to find
     * @return a defensive copy of the row, or null if not found
     */
    Map<String, Object> findById(String collection, long id);

    /**
     * Retrieves all rows from the named collection in insertion order.
     *
     * @param collection the collection name
     * @return an unmodifiable list of defensive copies, empty if collection does not exist
     */
    List<Map<String, Object>> findAll(String collection);

    /**
     * Retrieves all rows where the given field equals the given value.
     *
     * Uses exact equality (field value must equal searchValue). Null-safe: a field with null
     * value will match a searchValue of null.
     *
     * @param collection the collection name
     * @param field the field name to match
     * @param value the value to search for
     * @return an unmodifiable list of defensive copies, empty if no matches
     */
    List<Map<String, Object>> findByField(String collection, String field, Object value);

    /**
     * Inserts or updates a row in the named collection.
     *
     * If {@code id} is null, a new id is auto-generated. The row is always stored with the
     * canonical id injected into the row map under the "id" key, regardless of what the caller
     * supplied.
     *
     * @param collection the collection name
     * @param id the row id (or null to auto-generate)
     * @param row the row fields as a map; must not be null
     * @return the id of the stored row (auto-generated if id was null)
     * @throws NullPointerException if row is null
     */
    long upsert(String collection, Long id, Map<String, Object> row);

    /**
     * Deletes a row from the named collection by id.
     *
     * Safe to call if the row does not exist; does nothing.
     *
     * @param collection the collection name
     * @param id the row id to delete
     */
    void delete(String collection, long id);
}
