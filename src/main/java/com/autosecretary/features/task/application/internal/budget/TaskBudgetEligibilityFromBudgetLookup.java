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
        if (task == null || task.core == null || task.core.budgetRequiredCents == null || task.core.budgetRequiredCents <= 0) {
            return BudgetEligibility.passWithoutBudgetRequirement();
        }

        long availableCents;
        if (task.core.budgetAccountId != null && !task.core.budgetAccountId.isBlank()) {
            Long accountBalance = budgetLookupDao.findCurrentBalanceCentsByAccountId(task.core.budgetAccountId);
            availableCents = accountBalance != null ? accountBalance : 0L;
        } else {
            availableCents = budgetLookupDao.sumCurrentBalanceCentsForActiveAccounts();
        }
        return new BudgetEligibility(availableCents >= task.core.budgetRequiredCents, availableCents);
    }
}
