package com.autosecretary.features.budget.application;

import com.autosecretary.features.budget.domain.BudgetRepository;
import com.autosecretary.features.budget.domain.TransactionDirection;

import java.time.LocalDate;
import java.util.Objects;

public final class BudgetTransactionMutationUseCase {
    private final BudgetRepository repository;

    public BudgetTransactionMutationUseCase(BudgetRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public void create(String accountId,
                       String categoryId,
                       TransactionDirection direction,
                       long amountCents,
                       LocalDate date,
                       String note) {
        repository.saveTransaction(new BudgetRepository.TransactionCreateDetails(
                accountId, categoryId, direction, amountCents, date, note));
    }

    public void update(String transactionId,
                       String accountId,
                       String categoryId,
                       TransactionDirection direction,
                       long amountCents,
                       LocalDate date,
                       String note) {
        repository.updateTransaction(transactionId, new BudgetRepository.TransactionCreateDetails(
                accountId, categoryId, direction, amountCents, date, note));
    }

    public void delete(String transactionId) {
        repository.deleteTransaction(transactionId);
    }
}
