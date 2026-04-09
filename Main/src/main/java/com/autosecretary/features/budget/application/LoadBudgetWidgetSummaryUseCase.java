package com.autosecretary.features.budget.application;

import com.autosecretary.features.budget.domain.BudgetRepository;
import com.autosecretary.features.budget.domain.CategorySpendSummary;

import java.time.YearMonth;
import java.util.List;

/**
 * Loads summary data for the budget widget display.
 *
 * <p>Returns the current month's financial snapshot:
 * <ul>
 *   <li><strong>Net balance</strong> — sum of all account balances</li>
 *   <li><strong>Free budget</strong> — total remaining budget across all categories with limits</li>
 * </ul>
 *
 * <p>Used by the budget widget on the home screen and called whenever budget data changes.
 * Runs synchronously; suitable for quick summary calculations.
 *
 * @see BudgetWidgetSummary for result structure
 */
public class LoadBudgetWidgetSummaryUseCase {
    private final BudgetRepository repository;

    public LoadBudgetWidgetSummaryUseCase(BudgetRepository repository) {
        this.repository = repository;
    }

    public BudgetWidgetSummary execute() {
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
