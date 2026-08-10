package com.autosecretary.core;

/** Optional user-provided time-of-day anchor; learning is controlled separately by flexibility. */
public enum TimePreference {
    MORNING(8 * 60),
    MIDDAY(13 * 60),
    EVENING(18 * 60);

    private final int preferredMinute;

    TimePreference(int preferredMinute) {
        this.preferredMinute = preferredMinute;
    }

    public int preferredMinute() {
        return preferredMinute;
    }
}
