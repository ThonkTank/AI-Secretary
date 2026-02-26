package com.autosecretary.features.task.application.internal.budget;

import com.autosecretary.features.budget.data.entity.BudgetTransactionEntity;
import com.autosecretary.features.budget.data.repository.BudgetRoomRepository;
import com.autosecretary.features.task.data.Task;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Creates an automatic expense booking when a task completion requires budget.
 */
public class BookTaskCompletionExpenseUseCase {
    private final BudgetRoomRepository repository;

    public BookTaskCompletionExpenseUseCase(BudgetRoomRepository repository) {
        this.repository = repository;
    }

    public boolean execute(Task task, LocalDate bookingDate) {
        if (task == null || task.core == null || task.core.budgetRequiredCents == null) {
            return false;
        }

        long expenseCents = task.core.budgetRequiredCents;
        if (expenseCents <= 0) {
            return false;
        }

        String accountId = resolveAccountId(task);
        if (accountId == null) {
            return false;
        }

        BudgetTransactionEntity transaction = new BudgetTransactionEntity(
                accountId,
                task.core.budgetCategoryId,
                BudgetTransactionEntity.TransactionType.EXPENSE,
                expenseCents,
                bookingDate,
                YearMonth.from(bookingDate).toString()
        );
        transaction.note = "Task: " + task.core.title;

        repository.saveTransaction(transaction);
        repository.applyExpenseToAccountBalance(accountId, expenseCents);
        return true;
    }

    private String resolveAccountId(Task task) {
        String specificAccountId = task.core.budgetAccountId;
        if (specificAccountId != null && !specificAccountId.isBlank()) {
            return specificAccountId;
        }
        return repository.findDefaultActiveAccountId();
    }
}
