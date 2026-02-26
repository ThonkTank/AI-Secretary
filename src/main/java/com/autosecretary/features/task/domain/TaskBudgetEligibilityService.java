package com.autosecretary.features.task.domain;

import com.autosecretary.features.task.data.Task;

/**
 * Domain contract used by task scheduling to decide whether a task's budget gate passes.
 * Implementations may query budget/account/category data from any source.
 */
public interface TaskBudgetEligibilityService {

    BudgetEligibility eligibilityFor(Task task);

    record BudgetEligibility(boolean enoughBudget, long availableCents) {
        public static BudgetEligibility passWithoutBudgetRequirement() {
            return new BudgetEligibility(true, Long.MAX_VALUE);
        }
    }
}
