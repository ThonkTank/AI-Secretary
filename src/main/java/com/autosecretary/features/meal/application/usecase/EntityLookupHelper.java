package com.autosecretary.features.meal.application.usecase;

/**
 * Utility for repository lookups with mandatory entity existence.
 */
class EntityLookupHelper {

    /**
     * Ensures an entity was found, throws if null.
     *
     * @param entity The entity returned from repository lookup
     * @param entityType Descriptive name for error message (e.g., "Ingredient")
     * @param id The ID that was looked up
     * @return the non-null entity
     * @throws IllegalArgumentException if entity is null
     */
    static <T> T requireFound(T entity, String entityType, long id) {
        if (entity == null) {
            throw new IllegalArgumentException(entityType + " not found: id=" + id);
        }
        return entity;
    }

    private EntityLookupHelper() {
    }
}
