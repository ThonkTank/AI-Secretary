package de.thonktank.autosecretary.domain.model;

import java.util.Locale;

public enum StepAmountKind {
    NONE,
    SETS_REPS,
    REPS,
    DURATION;

    public String storageCode() { return name(); }

    public static StepAmountKind fromStorage(String value) {
        if (value == null || value.trim().isEmpty()) return NONE;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Unsupported step amount: " + value, error);
        }
    }
}
