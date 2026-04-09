package com.autosecretary.features.budget.ui.state;

/** Lookback window for the balance chart: rolling 30-day, 3-month, or 12-month view. */
public enum TimeRangeFilter {
    DAYS_30(0),
    MONTHS_3(3),
    MONTHS_12(12);

    /** Number of months in the lookback window; 0 for DAYS_30. */
    public final int months;

    TimeRangeFilter(int months) {
        this.months = months;
    }
}
