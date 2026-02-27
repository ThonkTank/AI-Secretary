package com.autosecretary.features.task.application.internal.budget;

import com.autosecretary.features.budget.data.dao.BudgetLookupDao;
import com.autosecretary.features.task.data.Task;
import com.autosecretary.features.task.domain.TaskBudgetEligibilityService;

/**
 * Default task-budget adapter backed by budget account balances.
 *
 * Product decision: scheduling only checks affordability and does not reserve funds.
 * Final booking/reservation behavior remains part of completion/payment workflows.
 */
public class TaskBudgetEligibilityFromBudgetLookup implements TaskBudgetEligibilityService {

    private final BudgetLookupDao budgetLookupDao;

    public TaskBudgetEligibilityFromBudgetLookup(BudgetLookupDao budgetLookupDao) {
        this.budgetLookupDao = budgetLookupDao;
    }

    @Override
    public BudgetEligibility eligibilityFor(Task task) {
        if (task == null || !task.hasBudgetRequirement()) {
            return BudgetEligibility.passWithoutBudgetRequirement();
        }

        long availableCents = getAvailableBalance(task.core.budgetAccountId);
        return new BudgetEligibility(availableCents >= task.core.budgetRequiredCents, availableCents);
    }

    private long getAvailableBalance(String accountId) {
        if (accountId != null && !accountId.isBlank()) {
            Long balance = budgetLookupDao.findCurrentBalanceCentsByAccountId(accountId);
            return balance != null ? balance : 0L;
        }
        return budgetLookupDao.sumCurrentBalanceCentsForActiveAccounts();
    }
}
