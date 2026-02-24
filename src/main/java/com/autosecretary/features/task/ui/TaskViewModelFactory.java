package com.autosecretary.features.task.ui;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.autosecretary.features.task.application.TaskUseCaseFactory;

public class TaskViewModelFactory implements ViewModelProvider.Factory {
    private final Application app;
    private final TaskUseCaseFactory.Bundle bundle;

    public TaskViewModelFactory(Application app, TaskUseCaseFactory.Bundle bundle) {
        this.app = app;
        this.bundle = bundle;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(TaskViewModel.class)) {
            return (T) new TaskViewModel(app, bundle);
        }
        throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
    }
}
