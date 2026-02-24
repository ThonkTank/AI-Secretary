package com.autosecretary.views;

import android.app.Application;

import com.autosecretary.application.task.TaskUseCaseFactory;
import com.autosecretary.config.Preferences;
import com.autosecretary.database.AppDatabase;
import com.autosecretary.database.task.TaskDAO;
import com.autosecretary.infrastructure.task.TaskDaoRepository;

public class AppCompositionRoot {
    private final Application app;

    public AppCompositionRoot(Application app) {
        this.app = app;
    }

    public TaskUseCaseFactory.Bundle createTaskUseCases() {
        AppDatabase db = AppDatabase.getInstance(app);
        TaskDAO taskDao = db.taskDao();
        TaskDaoRepository taskRepository = new TaskDaoRepository(taskDao);
        Preferences preferences = new Preferences(app);
        return TaskUseCaseFactory.create(taskRepository, preferences);
    }
}
