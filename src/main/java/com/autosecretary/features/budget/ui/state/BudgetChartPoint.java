package com.autosecretary.features.budget.ui.state;

public class BudgetChartPoint {
    private final String label;
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
