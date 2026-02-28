package com.autosecretary.features.budget.ui.state;

/**
 * Budget summary metrics for the current time range (30 days, 3 months, or 12 months).
 *
 * All amounts are in cents (divide by 100 for display in currency units).
 * Values are calculated from transactions in the selected time period.
 * Invariant: netCents == incomeCents − expenseCents.
 *
 * freeBudgetCents is the account's current running balance, not a derived
 * "remaining-within-limits" figure. It is read directly from
 * {@code BudgetAccount.currentBalanceCents} and may be positive (surplus)
 * or negative (overdraft). See {@code BudgetOverviewLoader.computeSummary()}.
 */
public class BudgetSummaryData {
    private final long incomeCents;
    private final long expenseCents;
    private final long netCents;
    /** Account running balance in cents; positive = surplus, negative = overdraft. */
    private final long freeBudgetCents;

    public BudgetSummaryData(long incomeCents, long expenseCents, long freeBudgetCents) {
        this.incomeCents = incomeCents;
        this.expenseCents = expenseCents;
        this.netCents = incomeCents - expenseCents;
        this.freeBudgetCents = freeBudgetCents;
    }

    public long getIncomeCents() {
        return incomeCents;
    }

    public long getExpenseCents() {
        return expenseCents;
    }

    public long getNetCents() {
        return netCents;
    }

    public long getFreeBudgetCents() {
        return freeBudgetCents;
    }
}
