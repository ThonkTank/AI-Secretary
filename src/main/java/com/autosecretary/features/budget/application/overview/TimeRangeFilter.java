package com.autosecretary.features.budget.application.overview;

import com.autosecretary.shared.ui.UiStateCarrier;

/** Lookback window for the balance chart: rolling 30-day, 3-month, or 12-month view. */
public enum TimeRangeFilter implements UiStateCarrier {
    DAYS_30(0),
    MONTHS_3(3),
    MONTHS_12(12);

    /** Number of months in the lookback window; 0 for DAYS_30. */
    public final int months;

    TimeRangeFilter(int months) {
        this.months = months;
    }
}
