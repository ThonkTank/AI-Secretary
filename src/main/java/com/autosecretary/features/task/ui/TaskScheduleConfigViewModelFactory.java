package com.autosecretary.features.task.ui;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.autosecretary.features.task.application.ScheduleReplanCoordinator;
import com.autosecretary.features.task.application.config.TaskScheduleConfigRepository;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

/**
 * Factory for {@link TaskScheduleConfigViewModel}.
 */
public class TaskScheduleConfigViewModelFactory implements ViewModelProvider.Factory {
    private final TaskScheduleConfigRepository repository;
    private final ScheduleReplanCoordinator scheduleReplanCoordinator;
    private final ExecutorService workerExecutor;
    private final Executor callbackDispatcher;

    public TaskScheduleConfigViewModelFactory(
            TaskScheduleConfigRepository repository,
            ScheduleReplanCoordinator scheduleReplanCoordinator,
            ExecutorService workerExecutor,
            Executor callbackDispatcher) {
        this.repository = repository;
        this.scheduleReplanCoordinator = scheduleReplanCoordinator;
        this.workerExecutor = workerExecutor;
        this.callbackDispatcher = callbackDispatcher;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(TaskScheduleConfigViewModel.class)) {
            return (T) new TaskScheduleConfigViewModel(repository, scheduleReplanCoordinator, workerExecutor, callbackDispatcher);
        }
        throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
    }
}
