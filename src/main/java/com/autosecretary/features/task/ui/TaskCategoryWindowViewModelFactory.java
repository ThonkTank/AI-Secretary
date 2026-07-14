package com.autosecretary.features.task.ui;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.autosecretary.features.task.application.config.TaskCategoryRepository;
import com.autosecretary.features.task.application.config.TaskCategoryWindowRepository;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

/**
 * Factory for {@link TaskCategoryWindowViewModel}.
 */
public class TaskCategoryWindowViewModelFactory implements ViewModelProvider.Factory {
    private final TaskCategoryWindowRepository windowRepository;
    private final TaskCategoryRepository categoryRepository;
    private final ExecutorService workerExecutor;
    private final Executor callbackDispatcher;

    public TaskCategoryWindowViewModelFactory(
            TaskCategoryWindowRepository windowRepository,
            TaskCategoryRepository categoryRepository,
            ExecutorService workerExecutor,
            Executor callbackDispatcher) {
        this.windowRepository = windowRepository;
        this.categoryRepository = categoryRepository;
        this.workerExecutor = workerExecutor;
        this.callbackDispatcher = callbackDispatcher;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(TaskCategoryWindowViewModel.class)) {
            return (T) new TaskCategoryWindowViewModel(windowRepository, categoryRepository, workerExecutor, callbackDispatcher);
        }
        throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
    }
}
