package com.autosecretary.features.budget.domain;

import com.autosecretary.features.budget.data.projection.CategorySpendTotal;

import java.util.List;

public interface BudgetWidgetRepository {
    long getNetBalanceCents();

    List<CategorySpendTotal> getCategorySpendTotals(String yearMonth);
}

