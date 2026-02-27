package com.autosecretary.features.budget.application;

import com.autosecretary.features.budget.data.dao.BudgetLimitDao;
import com.autosecretary.features.budget.data.dao.TransactionDao;
import com.autosecretary.features.budget.domain.CategorySpendSummary;

import java.time.YearMonth;
import java.util.List;

public class LoadBudgetWidgetSummaryUseCase {
    private final TransactionDao transactionDao;
    private final BudgetLimitDao budgetLimitDao;

    public LoadBudgetWidgetSummaryUseCase(TransactionDao transactionDao, BudgetLimitDao budgetLimitDao) {
        this.transactionDao = transactionDao;
        this.budgetLimitDao = budgetLimitDao;
    }

    public BudgetWidgetSummary loadCurrentMonth() {
        String yearMonth = YearMonth.now().toString();
        long netBalanceCents = transactionDao.getNetBalanceCents();

        List<CategorySpendSummary> spendTotals = budgetLimitDao.getCategorySpendTotals(yearMonth);
        long freeBudgetCents = spendTotals.stream()
                .filter(t -> t.limitAmountCents() > 0)
                .mapToLong(t -> t.limitAmountCents() - t.spentCents())
                .sum();

        return new BudgetWidgetSummary(netBalanceCents, freeBudgetCents);
    }

    public record BudgetWidgetSummary(long netBalanceCents, long freeBudgetCents) {
    }
}
