package com.autosecretary.features.budget.application;

import com.autosecretary.features.budget.data.projection.CategorySpendTotal;
import com.autosecretary.features.budget.domain.BudgetWidgetRepository;

import java.time.YearMonth;
import java.util.List;

public class LoadBudgetWidgetSummaryUseCase {
    private final BudgetWidgetRepository repository;

    public LoadBudgetWidgetSummaryUseCase(BudgetWidgetRepository repository) {
        this.repository = repository;
    }

    public BudgetWidgetSummary loadCurrentMonth() {
        String yearMonth = YearMonth.now().toString();
        long netBalanceCents = repository.getNetBalanceCents();

        List<CategorySpendTotal> spendTotals = repository.getCategorySpendTotals(yearMonth);
        long freeBudgetCents = 0;
        for (CategorySpendTotal total : spendTotals) {
            if (total.limitAmount <= 0) {
                continue;
            }
            long limitCents = Math.round(total.limitAmount * 100.0);
            freeBudgetCents += limitCents - total.spentCents;
        }

        return new BudgetWidgetSummary(netBalanceCents, freeBudgetCents);
    }

    public record BudgetWidgetSummary(long netBalanceCents, long freeBudgetCents) {
    }
}
