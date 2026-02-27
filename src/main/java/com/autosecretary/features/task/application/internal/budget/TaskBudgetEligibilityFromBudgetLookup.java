package com.autosecretary.features.task.application.internal.budget;

import com.autosecretary.features.budget.domain.BudgetRepository;
import com.autosecretary.features.task.data.Task;
import com.autosecretary.features.task.domain.TaskBudgetEligibilityService;

/**
 * Default task-budget adapter backed by budget account balances.
 *
 * Product decision: scheduling only checks affordability and does not reserve funds.
 * Final booking/reservation behavior remains part of completion/payment workflows.
 */
public class TaskBudgetEligibilityFromBudgetLookup implements TaskBudgetEligibilityService {

    private final BudgetRepository budgetRepository;

    public TaskBudgetEligibilityFromBudgetLookup(BudgetRepository budgetRepository) {
        this.budgetRepository = budgetRepository;
    }

    @Override
    public BudgetEligibility eligibilityFor(Task task) {
        if (task == null || !task.hasBudgetRequirement()) {
            return BudgetEligibility.passWithoutBudgetRequirement();
        }

        // Intentional: when budgetAccountId is null/blank, getCurrentBalanceCents aggregates
        // all active accounts (see BudgetRoomRepository.isAllAccounts). Scheduling asks
        // "can the user afford this at all?" — the separate question of which specific account
        // to debit is resolved by BookTaskCompletionExpenseUseCase at completion time.
        long availableCents = budgetRepository.getCurrentBalanceCents(task.core.budgetAccountId);
        return new BudgetEligibility(availableCents >= task.core.budgetRequiredCents, availableCents);
    }
}
