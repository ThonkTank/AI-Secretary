package com.autosecretary.features.task.ui.edit;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.autosecretary.features.task.application.ScheduleReplanCoordinator;
import com.autosecretary.features.task.application.TaskDataService;
import com.autosecretary.features.task.application.edit.CreateDefaultTaskPrefSlotUseCase;
import com.autosecretary.features.task.application.edit.TaskEditReferenceDataUseCase;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

public class TaskEditViewModelFactory implements ViewModelProvider.Factory {
    private final TaskDataService taskDataService;
    private final TaskEditReferenceDataUseCase referenceDataUseCase;
    private final CreateDefaultTaskPrefSlotUseCase createDefaultTaskPrefSlotUseCase;
    private final ScheduleReplanCoordinator scheduleReplanCoordinator;
    private final ExecutorService workerExecutor;
    private final Executor callbackDispatcher;

    public TaskEditViewModelFactory(
            TaskDataService taskDataService,
            TaskEditReferenceDataUseCase referenceDataUseCase,
            CreateDefaultTaskPrefSlotUseCase createDefaultTaskPrefSlotUseCase,
            ScheduleReplanCoordinator scheduleReplanCoordinator,
            ExecutorService workerExecutor,
            Executor callbackDispatcher) {
        this.taskDataService = taskDataService;
        this.referenceDataUseCase = referenceDataUseCase;
        this.createDefaultTaskPrefSlotUseCase = createDefaultTaskPrefSlotUseCase;
        this.scheduleReplanCoordinator = scheduleReplanCoordinator;
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
                    referenceDataUseCase,
                    createDefaultTaskPrefSlotUseCase,
                    scheduleReplanCoordinator,
                    workerExecutor,
                    callbackDispatcher
            );
        }
        throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
    }
}
