package com.autosecretary.features.task.ui;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.autosecretary.features.task.application.CheckOffTaskUseCase;
import com.autosecretary.features.task.application.DeleteTaskUseCase;
import com.autosecretary.features.task.application.RegenerateScheduleUseCase;
import com.autosecretary.features.task.application.TaskAsyncDataService;
import com.autosecretary.features.task.application.internal.calendar.CalendarReader;

public class TaskViewModelFactory implements ViewModelProvider.Factory {
    private final Application app;
    private final TaskAsyncDataService taskAsyncDataService;
    private final CheckOffTaskUseCase checkOffTaskUseCase;
    private final RegenerateScheduleUseCase regenerateScheduleUseCase;
    private final DeleteTaskUseCase deleteTaskUseCase;
    private final CalendarReader calendarReader;

    public TaskViewModelFactory(Application app,
                                TaskAsyncDataService taskAsyncDataService,
                                CheckOffTaskUseCase checkOffTaskUseCase,
                                RegenerateScheduleUseCase regenerateScheduleUseCase,
                                DeleteTaskUseCase deleteTaskUseCase,
                                CalendarReader calendarReader) {
        this.app = app;
        this.taskAsyncDataService = taskAsyncDataService;
        this.checkOffTaskUseCase = checkOffTaskUseCase;
        this.regenerateScheduleUseCase = regenerateScheduleUseCase;
        this.deleteTaskUseCase = deleteTaskUseCase;
        this.calendarReader = calendarReader;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(TaskViewModel.class)) {
            return (T) new TaskViewModel(
                    app,
                    taskAsyncDataService,
                    checkOffTaskUseCase,
                    regenerateScheduleUseCase,
                    deleteTaskUseCase,
                    calendarReader
            );
        }
        throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
    }
}
