package com.autosecretary.features.task.ui.list;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.autosecretary.app.Preferences;
import com.autosecretary.features.task.application.AdjustTaskProgressUseCase;
import com.autosecretary.features.task.application.CheckOffTaskUseCase;
import com.autosecretary.features.task.application.RegenerateScheduleUseCase;
import com.autosecretary.features.task.application.TaskAsyncDataService;
import com.autosecretary.features.task.application.calendar.TaskCalendarService;
import com.autosecretary.features.task.ui.edit.TaskEditSessionController;

public class TaskViewModelFactory implements ViewModelProvider.Factory {
    private final Application app;
    private final TaskAsyncDataService taskAsyncDataService;
    private final CheckOffTaskUseCase checkOffTaskUseCase;
    private final RegenerateScheduleUseCase regenerateScheduleUseCase;
    private final TaskCalendarService taskCalendarService;
    private final AdjustTaskProgressUseCase adjustTaskProgressUseCase;

    public TaskViewModelFactory(Application app,
                                TaskAsyncDataService taskAsyncDataService,
                                CheckOffTaskUseCase checkOffTaskUseCase,
                                RegenerateScheduleUseCase regenerateScheduleUseCase,
                                TaskCalendarService taskCalendarService,
                                AdjustTaskProgressUseCase adjustTaskProgressUseCase) {
        this.app = app;
        this.taskAsyncDataService = taskAsyncDataService;
        this.checkOffTaskUseCase = checkOffTaskUseCase;
        this.regenerateScheduleUseCase = regenerateScheduleUseCase;
        this.taskCalendarService = taskCalendarService;
        this.adjustTaskProgressUseCase = adjustTaskProgressUseCase;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(TaskViewModel.class)) {
            TaskEditSessionController taskEditSessionController = new TaskEditSessionController(
                    taskAsyncDataService
            );
            TaskListProjectionService taskListProjectionService = new TaskListProjectionService(
                    taskCalendarService,
                    new Preferences(app)
            );

            return (T) new TaskViewModel(
                    app,
                    taskAsyncDataService,
                    checkOffTaskUseCase,
                    regenerateScheduleUseCase,
                    adjustTaskProgressUseCase,
                    taskEditSessionController,
                    taskListProjectionService
            );
        }
        throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
    }
}
