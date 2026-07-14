package com.autosecretary.features.task.ui;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.autosecretary.features.task.application.RegenerateScheduleUseCase;
import com.autosecretary.features.task.application.config.TaskScheduleConfigRepository;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

/**
 * Factory for {@link TaskScheduleConfigViewModel}.
 */
public class TaskScheduleConfigViewModelFactory implements ViewModelProvider.Factory {
    private final TaskScheduleConfigRepository repository;
    private final RegenerateScheduleUseCase regenerateScheduleUseCase;
    private final ExecutorService workerExecutor;
    private final Executor callbackDispatcher;

    public TaskScheduleConfigViewModelFactory(
            TaskScheduleConfigRepository repository,
            RegenerateScheduleUseCase regenerateScheduleUseCase,
            ExecutorService workerExecutor,
            Executor callbackDispatcher) {
        this.repository = repository;
        this.regenerateScheduleUseCase = regenerateScheduleUseCase;
        this.workerExecutor = workerExecutor;
        this.callbackDispatcher = callbackDispatcher;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(TaskScheduleConfigViewModel.class)) {
            return (T) new TaskScheduleConfigViewModel(repository, regenerateScheduleUseCase, workerExecutor, callbackDispatcher);
        }
        throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
    }
}
