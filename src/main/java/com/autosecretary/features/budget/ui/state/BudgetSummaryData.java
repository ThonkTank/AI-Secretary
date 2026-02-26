package com.autosecretary.features.budget.ui.state;

public class BudgetSummaryData {
    private final long incomeCents;
    private final long expenseCents;
    private final long netCents;
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
