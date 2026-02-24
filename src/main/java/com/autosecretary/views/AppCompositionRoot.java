package com.autosecretary.views;

import android.app.Application;

import com.autosecretary.features.task.application.TaskUseCaseFactory;
import com.autosecretary.config.Preferences;
import com.autosecretary.database.AppDatabase;
import com.autosecretary.features.task.data.TaskDAO;

public class AppCompositionRoot {
    private final Application app;

    public AppCompositionRoot(Application app) {
        this.app = app;
    }

    public TaskUseCaseFactory.Bundle createTaskUseCases() {
        AppDatabase db = AppDatabase.getInstance(app);
        TaskDAO taskDao = db.taskDao();
        Preferences preferences = new Preferences(app);
        return TaskUseCaseFactory.create(taskDao, preferences);
    }
}
