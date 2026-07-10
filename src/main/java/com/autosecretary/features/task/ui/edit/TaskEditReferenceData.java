package com.autosecretary.features.task.ui.edit;

import java.util.List;

/**
 * Async reference data needed to populate the task-edit spinners.
 */
public record TaskEditReferenceData(
        List<TaskEditOption> parentTasks,
        List<TaskEditOption> budgetAccounts,
        List<TaskEditOption> budgetCategories) {
}
