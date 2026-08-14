package com.autosecretary.ui;

import java.util.Locale;

/** Stable, persisted filter for the complete work-item list. */
public enum WorkItemFilter {
    OPEN,
    ROUTINES,
    DONE;

    public String savedValue() { return name().toLowerCase(Locale.ROOT); }

    public static WorkItemFilter fromSavedValue(String value) {
        if (value == null) return OPEN;
        try { return valueOf(value.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ignored) { return OPEN; }
    }
}
