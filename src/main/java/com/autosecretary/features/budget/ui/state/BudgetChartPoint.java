package com.autosecretary.features.budget.ui.state;

/**
 * Single data point on the account balance timeline chart.
 *
 * Each point represents the account's running balance on a specific date.
 * Multiple points form a line chart showing balance changes over the selected time range.
 *
 * balanceCents is the absolute account balance at the date specified by label.
 * balanceCents can be positive (surplus) or negative (overdraft).
 */
public class BudgetChartPoint {
    /** Date label for this point, formatted for chart display (e.g., "Feb 15" or "1/15"). */
    private final String label;
    /** Account running balance in cents on the date specified by label. */
    private final long balanceCents;

    public BudgetChartPoint(String label, long balanceCents) {
        this.label = label;
        this.balanceCents = balanceCents;
    }

    public String getLabel() {
        return label;
    }

    public long getBalanceCents() {
        return balanceCents;
    }
}
