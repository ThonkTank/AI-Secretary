package com.autosecretary.features.budget.application;

import com.autosecretary.features.budget.domain.BudgetAccount;
import com.autosecretary.features.budget.domain.BudgetRepository;

import java.util.List;
import java.util.Objects;

public final class ResolveBudgetAccountUseCase {
    private final BudgetRepository repository;

    public ResolveBudgetAccountUseCase(BudgetRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public String execute(String selectedAccountId) {
        if (selectedAccountId != null && !selectedAccountId.isBlank()) {
            return selectedAccountId;
        }
        List<BudgetAccount> accounts = repository.findActiveAccounts();
        return accounts.isEmpty() ? null : accounts.get(0).id();
    }
}
