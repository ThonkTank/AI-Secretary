package com.autosecretary.features.meal.data.internal;

import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Abstraction for reading and writing the id field of a domain entity.
 *
 * <p>All meal domain entities have a public {@code id} field. This interface
 * encapsulates the accessor and setter logic so that {@link BaseCollectionDao}
 * can work with any entity type without specifying two separate lambdas.
 *
 * @param <T> the domain entity type
 */
public interface EntityIdHandler<T> {

    /**
     * Reads the id from the entity.
     *
     * @param entity the entity to read from (not null)
     * @return the entity's id, or null if not yet assigned
     */
    Long getId(T entity);

    /**
     * Sets the id on the entity.
     *
     * @param entity the entity to write to (not null)
     * @param id    the id to set (not null)
     */
    void setId(T entity, Long id);

    /**
     * Creates a handler from explicit getter and setter functions.
     *
     * <p>Used by repositories to create handlers without reflection,
     * maintaining Android compatibility.
     *
     * @param <T>    the entity type
     * @param getter function to read the id
     * @param setter function to write the id
     * @return an EntityIdHandler wrapping the provided functions
     */
    static <T> EntityIdHandler<T> of(Function<T, Long> getter, BiConsumer<T, Long> setter) {
        return new EntityIdHandler<T>() {
            @Override
            public Long getId(T entity) {
                return getter.apply(entity);
            }

            @Override
            public void setId(T entity, Long id) {
                setter.accept(entity, id);
            }
        };
    }
}
