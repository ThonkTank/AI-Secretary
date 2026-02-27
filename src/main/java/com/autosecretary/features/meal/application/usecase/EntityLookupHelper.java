package com.autosecretary.features.meal.application.usecase;

/**
 * Hilfsmethode fuer Repository-Lookups mit Pflichtexistenz-Pruefung.
 */
class EntityLookupHelper {

    /**
     * Stellt sicher, dass eine Entitaet gefunden wurde; wirft bei null.
     *
     * @param entity Das vom Repository zurueckgegebene Objekt
     * @param entityType Bezeichnung fuer die Fehlermeldung (z. B. "Ingredient")
     * @param id Die gesuchte ID
     * @return die nicht-null-Entitaet
     * @throws IllegalArgumentException wenn entity null ist
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
