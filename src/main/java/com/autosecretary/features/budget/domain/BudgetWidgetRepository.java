package com.autosecretary.features.budget.domain;

import java.util.List;

public interface BudgetWidgetRepository {
    long getNetBalanceCents();

    List<CategorySpendSummary> getCategorySpendTotals(String yearMonth);
}
