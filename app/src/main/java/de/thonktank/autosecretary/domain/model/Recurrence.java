package de.thonktank.autosecretary.domain.model;

import java.util.Locale;

public enum Recurrence {
    ONCE,
    DAILY,
    INTERVAL,
    WEEKDAYS;

    public String storageCode() {
        return name();
    }

    public static Recurrence fromStorage(String value) {
        if (value == null) throw new IllegalArgumentException("Recurrence must not be null");
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Unsupported recurrence: " + value, error);
        }
    }
}
