package com.autosecretary.features.budget.application;

import com.autosecretary.features.budget.domain.CategorySpendSummary;
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

        List<CategorySpendSummary> spendTotals = repository.getCategorySpendTotals(yearMonth);
        long freeBudgetCents = 0;
        for (CategorySpendSummary total : spendTotals) {
            if (total.limitAmountCents <= 0) {
                continue;
            }
            freeBudgetCents += total.limitAmountCents - total.spentCents;
        }

        return new BudgetWidgetSummary(netBalanceCents, freeBudgetCents);
    }

    public record BudgetWidgetSummary(long netBalanceCents, long freeBudgetCents) {
    }
}
