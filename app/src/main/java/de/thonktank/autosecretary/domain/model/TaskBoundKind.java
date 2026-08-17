package de.thonktank.autosecretary.domain.model;

import java.util.Locale;

public enum TaskBoundKind {
    FOREVER,
    UNTIL_DATE,
    FOR_WEEKS,
    N_TIMES;

    public String storageCode() { return name(); }

    public static TaskBoundKind fromStorage(String value) {
        if (value == null || value.trim().isEmpty()) return FOREVER;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Unsupported task bound: " + value, error);
        }
    }
}
