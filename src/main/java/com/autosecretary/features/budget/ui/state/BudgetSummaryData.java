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
public class BudgetSummaryData {
    private final long incomeCents;
    private final long expenseCents;
    /** Account running balance in cents; positive = surplus, negative = overdraft. */
    private final long freeBudgetCents;

    public BudgetSummaryData(long incomeCents, long expenseCents, long freeBudgetCents) {
        this.incomeCents = incomeCents;
        this.expenseCents = expenseCents;
        this.freeBudgetCents = freeBudgetCents;
    }

    public long getIncomeCents() {
        return incomeCents;
    }

    public long getExpenseCents() {
        return expenseCents;
    }

    public long getNetCents() {
        return incomeCents - expenseCents;
    }

    public long getFreeBudgetCents() {
        return freeBudgetCents;
    }
}
