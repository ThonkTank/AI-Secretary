package com.autosecretary.features.task.ui.list;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.autosecretary.app.Preferences;
import com.autosecretary.features.task.application.CheckOffTaskUseCase;
import com.autosecretary.features.task.application.DecrementTaskProgressUseCase;
import com.autosecretary.features.task.application.DeleteTaskUseCase;
import com.autosecretary.features.task.application.IncrementTaskProgressUseCase;
import com.autosecretary.features.task.application.RegenerateScheduleUseCase;
import com.autosecretary.features.task.application.TaskAsyncDataService;
import com.autosecretary.features.task.application.internal.calendar.CalendarReader;
import com.autosecretary.features.task.ui.edit.TaskEditSessionController;

public class TaskViewModelFactory implements ViewModelProvider.Factory {
    private final Application app;
    private final TaskAsyncDataService taskAsyncDataService;
    private final CheckOffTaskUseCase checkOffTaskUseCase;
    private final RegenerateScheduleUseCase regenerateScheduleUseCase;
    private final DeleteTaskUseCase deleteTaskUseCase;
    private final CalendarReader calendarReader;
    private final IncrementTaskProgressUseCase incrementTaskProgressUseCase;
    private final DecrementTaskProgressUseCase decrementTaskProgressUseCase;

    public TaskViewModelFactory(Application app,
                                TaskAsyncDataService taskAsyncDataService,
                                CheckOffTaskUseCase checkOffTaskUseCase,
                                RegenerateScheduleUseCase regenerateScheduleUseCase,
                                DeleteTaskUseCase deleteTaskUseCase,
                                CalendarReader calendarReader,
                                IncrementTaskProgressUseCase incrementTaskProgressUseCase,
                                DecrementTaskProgressUseCase decrementTaskProgressUseCase) {
        this.app = app;
        this.taskAsyncDataService = taskAsyncDataService;
        this.checkOffTaskUseCase = checkOffTaskUseCase;
        this.regenerateScheduleUseCase = regenerateScheduleUseCase;
        this.deleteTaskUseCase = deleteTaskUseCase;
        this.calendarReader = calendarReader;
        this.incrementTaskProgressUseCase = incrementTaskProgressUseCase;
        this.decrementTaskProgressUseCase = decrementTaskProgressUseCase;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(TaskViewModel.class)) {
            TaskEditSessionController taskEditSessionController = new TaskEditSessionController(
                    taskAsyncDataService,
                    deleteTaskUseCase
            );
            TaskListProjectionService taskListProjectionService = new TaskListProjectionService(
                    app,
                    calendarReader,
                    new Preferences(app)
            );

            return (T) new TaskViewModel(
                    app,
                    taskAsyncDataService,
                    checkOffTaskUseCase,
                    regenerateScheduleUseCase,
                    incrementTaskProgressUseCase,
                    decrementTaskProgressUseCase,
                    taskEditSessionController,
                    taskListProjectionService
            );
        }
        throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
    }
}
