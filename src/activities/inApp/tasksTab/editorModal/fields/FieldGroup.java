package activities.inApp.tasksTab.editorModal.fields;

import entities.TrackedItem;

/**
 * Interface fuer alle Field-Gruppen im Editor-Modal.
 * Jede Gruppe verwaltet ihre Views direkt — kein zentraler State-Layer.
 */
public interface FieldGroup {
    /** Befuellt Views aus Item (null = Create-Defaults). */
    void populate(TrackedItem item);

    /** Liest Werte aus Views und setzt sie auf den Builder. */
    void apply(TrackedItem.Builder builder);

    /** Aktualisiert Sichtbarkeit basierend auf berechneten Flags. */
    void updateVisibility(VisibilityFlags flags);
}
