package com.autosecretary.features.task.application.edit;

import com.autosecretary.features.budget.domain.BudgetCategory;
import com.autosecretary.features.budget.domain.BudgetRepository;
import com.autosecretary.features.task.application.TaskDataService;
import com.autosecretary.features.task.data.Task;

import java.util.List;
import java.util.Objects;

public final class TaskEditReferenceDataUseCase {
    private final TaskDataService taskDataService;
    private final BudgetRepository budgetRepository;

    public TaskEditReferenceDataUseCase(TaskDataService taskDataService,
                                        BudgetRepository budgetRepository) {
        this.taskDataService = Objects.requireNonNull(taskDataService, "taskDataService");
        this.budgetRepository = Objects.requireNonNull(budgetRepository, "budgetRepository");
    }

    public TaskEditReferenceData load(String currentTaskId) {
        List<TaskEditOption> parentTasks = taskDataService.readAllSync().stream()
                .filter(task -> isParentCandidate(task, currentTaskId))
                .map(task -> new TaskEditOption(task.core.id, task.core.title))
                .toList();
        List<TaskEditOption> budgetAccounts = budgetRepository.findActiveAccounts().stream()
                .map(account -> new TaskEditOption(account.id(), account.name()))
                .toList();
        List<TaskEditOption> budgetCategories = budgetRepository.findActiveCategories().stream()
                .map(category -> new TaskEditOption(category.id(), formatBudgetCategoryLabel(category)))
                .toList();
        return new TaskEditReferenceData(parentTasks, budgetAccounts, budgetCategories);
    }

    private static boolean isParentCandidate(Task task, String currentTaskId) {
        return task.core != null
                && task.core.id != null
                && task.core.title != null
                && !task.core.id.equals(currentTaskId);
    }

    private static String formatBudgetCategoryLabel(BudgetCategory category) {
        if (category.icon() == null || category.icon().isBlank()) {
            return category.name();
        }
        return category.icon() + " " + category.name();
    }
}
