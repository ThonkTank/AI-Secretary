package com.autosecretary.features.budget.application;

import com.autosecretary.features.budget.domain.BudgetRepository;
import com.autosecretary.features.budget.domain.CategorySpendSummary;

import java.time.YearMonth;
import java.util.List;

public class LoadBudgetWidgetSummaryUseCase {
    private final BudgetRepository repository;

    public LoadBudgetWidgetSummaryUseCase(BudgetRepository repository) {
        this.repository = repository;
    }

    public BudgetWidgetSummary loadCurrentMonth() {
        String yearMonth = YearMonth.now().toString();
        long netBalanceCents = repository.getNetBalanceCents();

        List<CategorySpendSummary> spendTotals = repository.getCategorySpendTotals(yearMonth);
        long freeBudgetCents = spendTotals.stream()
                .filter(t -> t.limitAmountCents() > 0)
                .mapToLong(t -> t.limitAmountCents() - t.spentCents())
                .sum();

        return new BudgetWidgetSummary(netBalanceCents, freeBudgetCents);
    }

    public record BudgetWidgetSummary(long netBalanceCents, long freeBudgetCents) {
    }
}
