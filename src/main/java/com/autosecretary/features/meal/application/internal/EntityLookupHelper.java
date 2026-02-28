package com.autosecretary.features.meal.application.internal;

/**
 * Utility for asserting that a repository lookup returned a result.
 * Do not instantiate — use the static method directly.
 */
public class EntityLookupHelper {

    /**
     * Asserts that a repository lookup returned a non-null result.
     *
     * @param entity     the object returned by the repository; may be null
     * @param entityType human-readable name used in the error message (e.g. "Ingredient")
     * @param id         the id that was looked up; included in the error message
     * @return {@code entity} unchanged (never null)
     * @throws IllegalArgumentException if {@code entity} is null
     */
    public static <T> T requireFound(T entity, String entityType, long id) {
        if (entity == null) {
            throw new IllegalArgumentException(entityType + " not found: id=" + id);
        }
        return entity;
    }

    private EntityLookupHelper() {
    }
}
