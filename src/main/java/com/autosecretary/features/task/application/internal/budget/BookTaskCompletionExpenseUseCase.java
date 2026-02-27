package com.autosecretary.features.task.application.internal.budget;

import com.autosecretary.features.budget.data.entity.BudgetTransactionEntity;
import com.autosecretary.features.budget.domain.BudgetRepository;
import com.autosecretary.features.budget.domain.TransactionDirection;
import com.autosecretary.features.task.data.Task;

import java.time.LocalDate;

/**
 * Creates an automatic expense booking when a task completion requires budget.
 */
public class BookTaskCompletionExpenseUseCase {
    private final BudgetRepository repository;

    public BookTaskCompletionExpenseUseCase(BudgetRepository repository) {
        this.repository = repository;
    }

    public boolean execute(Task task, LocalDate bookingDate) {
        if (task == null || !task.hasBudgetRequirement()) {
            return false;
        }

        long expenseCents = task.core.budgetRequiredCents;

        String accountId = resolveAccountId(task.core.budgetAccountId);
        if (accountId == null) {
            return false;
        }

        BudgetTransactionEntity transaction = new BudgetTransactionEntity(
                accountId,
                task.core.budgetCategoryId,
                TransactionDirection.EXPENSE,
                expenseCents,
                bookingDate
        );
        transaction.note = "Task: " + task.core.title;

        repository.saveTransactionAndDeductBalance(transaction, accountId, expenseCents);
        return true;
    }

    private String resolveAccountId(String providedAccountId) {
        if (providedAccountId != null && !providedAccountId.isBlank()) {
            return providedAccountId;
        }
        return repository.findDefaultActiveAccountId();
    }
}
