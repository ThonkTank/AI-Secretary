package de.thonktank.autosecretary.domain.model;

/** Explicitly distinguishes calendar work, a condition completion and internal flow execution. */
public enum OccurrenceKind {
    SCHEDULED,
    CONDITION,
    FLOW_STEP;

    public static OccurrenceKind fromStorage(String value) {
        if (value == null) return SCHEDULED;
        try {
            return valueOf(value);
        } catch (IllegalArgumentException invalid) {
            return SCHEDULED;
        }
    }
}
