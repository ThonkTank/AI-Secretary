package com.autosecretary.features.task.ui;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.autosecretary.features.task.application.CheckOffTaskUseCase;
import com.autosecretary.features.task.application.LoadTaskListUseCase;
import com.autosecretary.features.task.application.RegenerateScheduleUseCase;
import com.autosecretary.features.task.application.SaveTaskUseCase;

public class TaskViewModelFactory implements ViewModelProvider.Factory {
    private final Application app;
    private final LoadTaskListUseCase loadTaskListUseCase;
    private final SaveTaskUseCase saveTaskUseCase;
    private final CheckOffTaskUseCase checkOffTaskUseCase;
    private final RegenerateScheduleUseCase regenerateScheduleUseCase;

    public TaskViewModelFactory(Application app,
                                LoadTaskListUseCase loadTaskListUseCase,
                                SaveTaskUseCase saveTaskUseCase,
                                CheckOffTaskUseCase checkOffTaskUseCase,
                                RegenerateScheduleUseCase regenerateScheduleUseCase) {
        this.app = app;
        this.loadTaskListUseCase = loadTaskListUseCase;
        this.saveTaskUseCase = saveTaskUseCase;
        this.checkOffTaskUseCase = checkOffTaskUseCase;
        this.regenerateScheduleUseCase = regenerateScheduleUseCase;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(TaskViewModel.class)) {
            return (T) new TaskViewModel(
                    app,
                    loadTaskListUseCase,
                    saveTaskUseCase,
                    checkOffTaskUseCase,
                    regenerateScheduleUseCase
            );
        }
        throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
    }
}
