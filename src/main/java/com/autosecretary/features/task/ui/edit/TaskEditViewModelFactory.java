package com.autosecretary.features.task.ui.edit;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.autosecretary.features.budget.domain.BudgetRepository;
import com.autosecretary.features.task.application.TaskDataService;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

public class TaskEditViewModelFactory implements ViewModelProvider.Factory {
    private final TaskDataService taskDataService;
    private final BudgetRepository budgetRepository;
    private final ExecutorService workerExecutor;
    private final Executor callbackDispatcher;

    public TaskEditViewModelFactory(
            TaskDataService taskDataService,
            BudgetRepository budgetRepository,
            ExecutorService workerExecutor,
            Executor callbackDispatcher) {
        this.taskDataService = taskDataService;
        this.budgetRepository = budgetRepository;
        this.workerExecutor = workerExecutor;
        this.callbackDispatcher = callbackDispatcher;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(TaskEditViewModel.class)) {
            return (T) new TaskEditViewModel(
                    taskDataService,
                    budgetRepository,
                    workerExecutor,
                    callbackDispatcher
            );
        }
        throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
    }
}
