package com.autosecretary.features.budget.ui.state;

/**
 * Budget summary metrics for the currently selected month ({@code BudgetViewModel.currentMonth}).
 *
 * <p>Note: the time-range filter (30d / 3m / 12m) controls only the balance <em>chart</em>;
 * this summary is always scoped to the selected calendar month, not the chart window.
 *
 * All amounts are in cents (divide by 100 for display in currency units).
 * Values are calculated from transactions in the selected month.
 * Invariant: netCents == incomeCents − expenseCents.
 *
 * freeBudgetCents is the account's current running balance, not a derived
 * "remaining-within-limits" figure. It is read directly from
 * {@code BudgetAccountEntity.currentBalanceCents} and may be positive (surplus)
 * or negative (overdraft). See {@code BudgetOverviewLoader.computeSummary()}.
 */
public record BudgetSummaryData(long incomeCents, long expenseCents, long freeBudgetCents) {

    public long netCents() {
        return incomeCents - expenseCents;
    }
}
