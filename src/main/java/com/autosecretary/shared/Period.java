package com.autosecretary.shared;

/**
 * Scheduling period units with fixed day counts for repetition patterns.
 * <p>
 * MONTH uses 30 days as a fixed approximation for scheduling purposes, not a calendar month.
 * This simplifies scheduling logic without respecting actual month boundaries.
 */
public enum Period {
    DAY(1),
    WEEK(7),
    MONTH(30);

    /**
     * Fixed number of days in this scheduling period.
     * MONTH=30 is an approximation; not adjusted for actual calendar month length.
     */
    public final int dayCount;
    Period(int dayCount) { this.dayCount = dayCount; }
}
