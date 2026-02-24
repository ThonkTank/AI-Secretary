package com.autosecretary.views;

import android.app.Application;
import android.util.Log;

import com.autosecretary.config.Preferences;
import com.autosecretary.database.AppDatabase;
import com.autosecretary.features.task.application.CheckOffTaskUseCase;
import com.autosecretary.features.task.application.LoadTaskListUseCase;
import com.autosecretary.features.task.application.RegenerateScheduleUseCase;
import com.autosecretary.features.task.application.SaveTaskUseCase;
import com.autosecretary.features.task.application.mapper.TaskListItemMapper;
import com.autosecretary.features.task.data.TaskDAO;
import com.autosecretary.features.task.domain.SlotGenerator;
import com.autosecretary.features.task.domain.TaskCompletionService;
import com.autosecretary.features.task.domain.TaskLifecycleManager;
import com.autosecretary.features.task.domain.TaskScorer;
import com.autosecretary.features.task.domain.TimeWindow;
import com.autosecretary.features.task.ui.TaskViewModelFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AppCompositionRoot {
    private final Application app;

    public AppCompositionRoot(Application app) {
        this.app = app;
    }

    public TaskViewModelFactory createTaskViewModelFactory() {
        AppDatabase db = AppDatabase.getInstance(app);
        TaskDAO taskDao = db.taskDao();
        Preferences preferences = new Preferences(app);

        ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable);
            thread.setUncaughtExceptionHandler((t, e) ->
                    Log.e("TaskUseCase", "Background crash", e)
            );
            return thread;
        });

        TaskLifecycleManager lifecycleManager = new TaskLifecycleManager();
        TaskCompletionService completionService = new TaskCompletionService();
        TaskScorer scorer = new TaskScorer(lifecycleManager);
        SlotGenerator generator = new SlotGenerator(scorer, message -> Log.d("SlotGen", message));
        TaskListItemMapper mapper = new TaskListItemMapper();

        LoadTaskListUseCase loadTaskListUseCase = new LoadTaskListUseCase(taskDao, mapper, executor);
        SaveTaskUseCase saveTaskUseCase = new SaveTaskUseCase(taskDao, executor);
        CheckOffTaskUseCase checkOffTaskUseCase = new CheckOffTaskUseCase(
                taskDao,
                completionService,
                lifecycleManager,
                executor
        );
        RegenerateScheduleUseCase regenerateScheduleUseCase = new RegenerateScheduleUseCase(
                taskDao,
                generator,
                () -> {
                    LocalDate day = LocalDate.now();
                    LocalDateTime start = LocalDateTime.of(day, preferences.readPrefTime(day, true));
                    LocalDateTime end = LocalDateTime.of(day, preferences.readPrefTime(day, false));
                    return new TimeWindow(start, end);
                },
                executor
        );

        return new TaskViewModelFactory(
                app,
                loadTaskListUseCase,
                saveTaskUseCase,
                checkOffTaskUseCase,
                regenerateScheduleUseCase
        );
    }
}
