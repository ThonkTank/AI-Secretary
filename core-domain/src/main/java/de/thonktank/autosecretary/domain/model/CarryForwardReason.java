package de.thonktank.autosecretary.domain.model;

import java.util.Locale;

/** Explains why a step is present in a later active occurrence. */
public enum CarryForwardReason {
    NONE,
    UNFINISHED_STEP;

    public String storageCode() {
        return name();
    }

    public static CarryForwardReason fromStorage(String value) {
        if (value == null || value.trim().isEmpty()) return NONE;
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
