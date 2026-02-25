package com.autosecretary.features.budget.domain;

import java.time.LocalDate;

public class CalculateFreeBudgetUseCase {
    private final BudgetRepository repository;

    public CalculateFreeBudgetUseCase(BudgetRepository repository) {
        this.repository = repository;
    }

    public long execute(String accountId, LocalDate today, int lookAheadDays) {
        LocalDate toDate = today.plusDays(Math.max(0, lookAheadDays));
        long baseBalance = repository.getCurrentBalanceCents(accountId);
        long upcomingExpenses = repository.getUpcomingExpenseTemplateCents(accountId, today, toDate);
        return baseBalance - upcomingExpenses;
    }
}
