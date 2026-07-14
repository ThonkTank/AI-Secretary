package com.autosecretary.features.task.application.edit;

import com.autosecretary.features.budget.domain.BudgetCategory;
import com.autosecretary.features.budget.domain.BudgetRepository;
import com.autosecretary.features.task.application.TaskDataService;

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
        List<TaskEditOption> categories = taskDataService.readAllCategoriesSync().stream()
                .map(category -> new TaskEditOption(category.id, formatCategoryLabel(category.icon, category.name)))
                .toList();
        List<TaskEditOption> budgetAccounts = budgetRepository.findActiveAccounts().stream()
                .map(account -> new TaskEditOption(account.id(), account.name()))
                .toList();
        List<TaskEditOption> budgetCategories = budgetRepository.findActiveCategories().stream()
                .map(category -> new TaskEditOption(category.id(), formatBudgetCategoryLabel(category)))
                .toList();
        return new TaskEditReferenceData(categories, budgetAccounts, budgetCategories);
    }

    private static String formatCategoryLabel(String icon, String name) {
        if (icon == null || icon.isBlank()) {
            return name;
        }
        return icon + " " + name;
    }

    private static String formatBudgetCategoryLabel(BudgetCategory category) {
        if (category.icon() == null || category.icon().isBlank()) {
            return category.name();
        }
        return category.icon() + " " + category.name();
    }
}
