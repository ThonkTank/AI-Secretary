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

/**
 * Generic CRUD DAO that connects a typed domain entity {@code T} to an untyped
 * {@link MealStorage} collection.
 *
 * <p>This class sits between a repository ({@link com.autosecretary.features.meal.data.internal.repository})
 * and the raw storage layer:
 * <pre>
 *   StorageFooRepository → BaseCollectionDao&lt;Foo&gt; → RowMapper&lt;Foo&gt; → MealStorage
 * </pre>
 *
 * <h3>Why lambdas instead of reflection?</h3>
 * <p>{@code idAccessor} and {@code idSetter} are function parameters rather than using Java
 * reflection to read/write the {@code id} field. This keeps the class reflection-free, which
 * is important on Android (reflection is slow and affected by R8/ProGuard rules). Repositories
 * pass simple lambdas that directly access the id field:
 * <pre>
 *   new BaseCollectionDao&lt;&gt;(
 *       MealCollections.RECIPES,
 *       storage,
 *       new RecipeRowMapper(),
 *       r -&gt; r.id,             // idAccessor: read existing id
 *       (r, id) -&gt; r.id = id   // idSetter: inject generated id back
 *   );
 * </pre>
 *
 * <h3>ID generation</h3>
 * <p>When {@code save()} is called with an entity whose id is null, the storage layer
 * auto-generates an id and sets it back on the entity via {@code idSetter}. If the id
 * is already non-null, the existing id is used (upsert semantics).
 *
 * @param <T> the domain entity type managed by this DAO
 * @see RowMapper
 * @see MealStorage
 * @see MealCollections
 */
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

    /**
     * Finds a single entity by id.
     *
     * @param id the entity id to find
     * @return the entity, or null if not found
     */
    public T findById(long id) {
        Map<String, Object> row = storage.findById(collection, id);
        if (row == null) {
            return null;
        }
        return mapper.fromRow(row);
    }

    /**
     * Returns all entities in this collection, in insertion order.
     *
     * @return all entities; empty list if the collection is empty
     */
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

    /**
     * Deletes the entity with the given id from the collection.
     *
     * <p>Safe to call if the entity does not exist; does nothing in that case.
     *
     * @param id the entity id to delete
     */
    public void deleteById(long id) {
        storage.delete(collection, id);
    }

    /**
     * Returns all entities in this collection that satisfy the given predicate.
     *
     * <p><strong>Note:</strong> This is a full-collection scan — all rows are loaded from
     * storage and deserialized before the predicate is applied. Avoid in hot paths or on
     * large collections. For indexed lookups, prefer {@link #findAllByField}.
     *
     * @param filter predicate applied to each deserialized entity
     * @return matching entities; empty list if none match
     */
    public List<T> findAll(Predicate<T> filter) {
        return findAll().stream().filter(filter).collect(Collectors.toList());
    }

    /**
     * Returns all entities where the given field equals the given value (equality check).
     *
     * <p>Field names must match the constants in {@link MealFieldKeys}. Matching is
     * done at the storage level (before deserialization), so this is more efficient than
     * {@link #findAll(Predicate)} for single-field equality queries.
     *
     * @param field the field name to match (a constant from {@link MealFieldKeys})
     * @param value the value to match; null matches fields where the stored value is also null
     * @return matching entities; empty list if none match
     */
    public List<T> findAllByField(String field, Object value) {
        return mapRows(storage.findByField(collection, field, value));
    }

    /**
     * Returns a single entity where the given field equals the given value, or null if not found.
     *
     * <p>This method is more efficient than {@link #findAllByField(String, Object)} when you expect
     * at most one result, as it allows the storage layer to optimize for early termination.
     * If multiple matches exist, only the first is returned.
     *
     * @param field the field name to match (a constant from {@link MealFieldKeys})
     * @param value the value to match; null matches fields where the stored value is also null
     * @return the first matching entity, or null if none match
     */
    public T findSingleByField(String field, Object value) {
        List<Map<String, Object>> rows = storage.findByField(collection, field, value);
        if (rows.isEmpty()) {
            return null;
        }
        return mapper.fromRow(rows.get(0));
    }

    private List<T> mapRows(List<Map<String, Object>> rows) {
        return rows.stream().map(mapper::fromRow).collect(Collectors.toList());
    }
}
