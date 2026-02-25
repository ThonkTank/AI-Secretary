package com.autosecretary.features.budget.domain;

import com.autosecretary.features.budget.data.entity.BudgetAccount;

import java.time.LocalDate;

public class CalculateFreeBudgetUseCase {
    private final BudgetRepository repository;

    public CalculateFreeBudgetUseCase(BudgetRepository repository) {
        this.repository = repository;
    }

    public long execute(String accountId, LocalDate today, int lookAheadDays) {
        BudgetAccount account = repository.findAccountById(accountId);
        return account != null ? account.currentBalanceCents : 0L;
    }
}
