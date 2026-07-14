package com.autosecretary.features.task.application.edit;

import java.util.List;

/**
 * Async reference data needed to populate the task-edit spinners.
 */
public record TaskEditReferenceData(
        List<TaskEditOption> categories,
        List<TaskEditOption> budgetAccounts,
        List<TaskEditOption> budgetCategories) {
}
