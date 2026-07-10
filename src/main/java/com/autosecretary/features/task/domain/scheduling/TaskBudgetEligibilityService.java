package com.autosecretary.features.task.domain.scheduling;

import com.autosecretary.features.task.data.Task;

/**
 * Domain contract used by task scheduling to decide whether a task's budget requirement can be met.
 *
 * <p><strong>Purpose:</strong> Called during slot scoring and scheduling to gate task feasibility.
 * If a task requires budget and the user cannot afford it, the task is ineligible for scheduling.
 *
 * <p><strong>Lifecycle:</strong> Invoked by scheduling algorithms (slot scoring, feasibility checks).
 * Separate from actual booking, which happens at completion time via
 * {@code BookTaskCompletionExpenseUseCase}.
 *
 * <p><strong>Implementations:</strong> May query budget/account/category data from any source
 * (database, external API, etc.). Default implementation ({@code TaskBudgetEligibilityFromBudgetLookup})
 * queries current account balance from the budget repository.
 *
 * @see com.autosecretary.features.task.application.internal.budget.TaskBudgetEligibilityFromBudgetLookup
 * @see com.autosecretary.features.task.application.internal.budget.BookTaskCompletionExpenseUseCase
 */
public interface TaskBudgetEligibilityService {

    /**
     * Determines whether a task's budget requirement can be satisfied.
     *
     * @param task Task to evaluate (may have {@code budgetRequiredCents == 0}, indicating no constraint)
     * @return {@code true} when the task is affordable enough to be considered for scheduling
     */
    boolean eligibilityFor(Task task);
}
