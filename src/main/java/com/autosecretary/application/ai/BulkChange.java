package com.autosecretary.application.ai;

import com.autosecretary.domain.WorkItem;

import java.util.Objects;
import java.util.UUID;

/** One typed, independently selectable proposal row. */
public record BulkChange(
        String changeId,
        Type type,
        String targetId,
        long expectedRevision,
        WorkItem upsert,
        String previewLabel) {
    public enum Type { ADD, UPDATE, DELETE }

    public BulkChange {
        requireUuid(changeId, "Change-ID");
        requireUuid(targetId, "Ziel-ID");
        type = Objects.requireNonNull(type, "Änderungstyp fehlt");
        if (expectedRevision < 0) {
            throw new IllegalArgumentException("Erwartete Revision darf nicht negativ sein");
        }
        if (type == Type.DELETE && upsert != null) {
            throw new IllegalArgumentException("Löschung darf kein Ersatzobjekt enthalten");
        }
        if (type != Type.DELETE && upsert == null) {
            throw new IllegalArgumentException("Hinzufügen oder Ändern benötigt ein Work Item");
        }
        if (upsert != null && !targetId.equals(upsert.id())) {
            throw new IllegalArgumentException("Ziel-ID und Work Item widersprechen sich");
        }
        if (type == Type.ADD && expectedRevision != 0) {
            throw new IllegalArgumentException("Neue Work Items beginnen bei Revision 0");
        }
        if (type == Type.ADD && upsert.revision() != 0) {
            throw new IllegalArgumentException("Neues Work Item muss Revision 0 tragen");
        }
        if (type == Type.UPDATE && upsert.revision() != expectedRevision) {
            throw new IllegalArgumentException(
                    "Update und erwartete Revision widersprechen sich");
        }
        previewLabel = previewLabel == null ? "" : previewLabel.trim();
        if (previewLabel.isEmpty()) {
            throw new IllegalArgumentException("Vorschautext fehlt");
        }
    }

    private static void requireUuid(String value, String label) {
        try { UUID.fromString(value); }
        catch (RuntimeException error) {
            throw new IllegalArgumentException(label + " ist keine UUID", error);
        }
    }
}
