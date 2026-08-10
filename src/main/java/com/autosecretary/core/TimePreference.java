package com.autosecretary.core;

/** Optional user-provided anchor; null means the planner learns from completions. */
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
