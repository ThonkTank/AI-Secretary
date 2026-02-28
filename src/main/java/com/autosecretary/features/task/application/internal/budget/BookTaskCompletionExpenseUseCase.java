package com.autosecretary.features.task.application.internal.budget;

import com.autosecretary.features.budget.domain.BudgetRepository;
import com.autosecretary.features.task.data.Task;

import java.time.LocalDate;

/**
 * Records an expense transaction when a task with a budget requirement completes.
 *
 * <p><strong>Purpose:</strong> Complement to {@code TaskBudgetEligibilityService}. While eligibility
 * is checked during scheduling, booking happens at completion to record what was actually spent.
 *
 * <p><strong>Lifecycle:</strong> Called by {@code CheckOffTaskUseCase} during task completion, after
 * the slot state is finalized. Only books expense if task has {@code budgetRequiredCents > 0}.
 *
 * <p><strong>Account resolution:</strong> If task is linked to a specific account
 * ({@code budgetAccountId} is non-blank), deducts from that account. Otherwise, falls back to the
 * default active account (see {@code resolveAccountId()}).
 *
 * @see com.autosecretary.features.task.domain.scheduling.TaskBudgetEligibilityService
 */
public class BookTaskCompletionExpenseUseCase {
    private final BudgetRepository repository;

    public BookTaskCompletionExpenseUseCase(BudgetRepository repository) {
        this.repository = repository;
    }

    /**
     * Executes the booking if the task has a budget requirement.
     *
     * @param task Task that was completed (may have no budget requirement)
     * @param bookingDate Date to record the expense as occurring
     * @return {@code true} if booking succeeded; {@code false} if task has no budget requirement or
     *         account resolution failed
     */
    public boolean execute(Task task, LocalDate bookingDate) {
        // Guard: Only book expense if task actually requires budget.
        // Null task should not happen in production, but we defend against it.
        if (task == null || !task.hasBudgetRequirement()) {
            return false;
        }

        long expenseCents = task.core.budgetRequiredCents;

        String accountId = resolveAccountId(task.core.budgetAccountId);
        if (accountId == null) {
            return false;
        }

        repository.bookExpenseAndDeductBalance(
                accountId,
                task.core.budgetCategoryId,
                expenseCents,
                bookingDate,
                "Task: " + task.core.title
        );
        return true;
    }

    /**
     * Resolves which account to debit. If the task is linked to a specific account, uses it.
     * Otherwise falls back to the default active account (set in budget settings).
     *
     * <p>This fallback enables task templates to be reused across different account configurations.
     * A "Groceries" task can belong to a personal account for one user and a household account
     * for another, without duplicating the task definition.
     *
     * @param providedAccountId Account ID linked to the task (may be null or blank)
     * @return Resolved account ID, never null
     */
    private String resolveAccountId(String providedAccountId) {
        if (providedAccountId != null && !providedAccountId.isBlank()) {
            return providedAccountId;
        }
        return repository.findDefaultActiveAccountId();
    }
}
