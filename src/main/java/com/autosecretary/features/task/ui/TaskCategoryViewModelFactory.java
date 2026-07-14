package com.autosecretary.features.task.ui;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.autosecretary.features.task.application.config.TaskCategoryRepository;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

/**
 * Factory for {@link TaskCategoryViewModel}.
 */
public class TaskCategoryViewModelFactory implements ViewModelProvider.Factory {
    private final TaskCategoryRepository repository;
    private final ExecutorService workerExecutor;
    private final Executor callbackDispatcher;

    public TaskCategoryViewModelFactory(
            TaskCategoryRepository repository,
            ExecutorService workerExecutor,
            Executor callbackDispatcher) {
        this.repository = repository;
        this.workerExecutor = workerExecutor;
        this.callbackDispatcher = callbackDispatcher;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(TaskCategoryViewModel.class)) {
            return (T) new TaskCategoryViewModel(repository, workerExecutor, callbackDispatcher);
        }
        throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
    }
}
