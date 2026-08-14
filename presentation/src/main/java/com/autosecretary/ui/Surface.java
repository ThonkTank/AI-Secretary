package com.autosecretary.ui;

import java.util.Locale;

/** Stable, persisted identifier for the three top-level destinations. */
public enum Surface {
    TODAY,
    ALL,
    AI;

    public String savedValue() { return name().toLowerCase(Locale.ROOT); }

    public static Surface fromSavedValue(String value) {
        if (value == null) return TODAY;
        try { return valueOf(value.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ignored) { return TODAY; }
    }
}
