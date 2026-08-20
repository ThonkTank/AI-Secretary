package de.thonktank.autosecretary.domain.model;

import java.util.Locale;

public enum OccurrenceState {
    OPEN,
    COMPLETED,
    MISSED;

    public String storageCode() {
        return name();
    }

    public static OccurrenceState fromStorage(String value) {
        if (value == null) throw new IllegalArgumentException("Occurrence state must not be null");
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Unsupported occurrence state: " + value, error);
        }
    }
}
