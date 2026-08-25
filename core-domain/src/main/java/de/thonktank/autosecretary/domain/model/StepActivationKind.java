package de.thonktank.autosecretary.domain.model;

/** Determines whether a template is scheduled by its cadence or unlocked by another step. */
public enum StepActivationKind {
    SCHEDULED,
    FOLLOW_UP;

    public String storageCode() { return name(); }

    public static StepActivationKind fromStorage(String value) {
        if (value == null) return SCHEDULED;
        try {
            return valueOf(value);
        } catch (IllegalArgumentException invalid) {
            return SCHEDULED;
        }
    }
}
