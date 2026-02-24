package com.autosecretary.features.task.application;

import android.util.Log;

import com.autosecretary.features.task.application.mapper.TaskListItemMapper;
import com.autosecretary.config.Preferences;
import com.autosecretary.features.task.data.TaskDAO;
import com.autosecretary.features.task.domain.TaskCompletionService;
import com.autosecretary.features.task.domain.TaskLifecycleManager;
import com.autosecretary.features.task.domain.SlotGenerator;
import com.autosecretary.features.task.domain.TaskScorer;
import com.autosecretary.features.task.domain.TimeWindow;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class TaskUseCaseFactory {

    private TaskUseCaseFactory() {}

    public static Bundle create(TaskDAO taskDao, Preferences prefs) {
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

        return new Bundle(
                new LoadTaskListUseCase(taskDao, mapper, executor),
                new SaveTaskUseCase(taskDao, executor),
                new CheckOffTaskUseCase(taskDao, completionService, lifecycleManager, executor),
                new RegenerateScheduleUseCase(taskDao, generator, () -> {
                    LocalDate day = LocalDate.now();
                    LocalDateTime start = LocalDateTime.of(day, prefs.readPrefTime(day, true));
                    LocalDateTime end = LocalDateTime.of(day, prefs.readPrefTime(day, false));
                    return new TimeWindow(start, end);
                }, executor)
        );
    }

    public static class Bundle {
        public final LoadTaskListUseCase loadTaskListUseCase;
        public final SaveTaskUseCase saveTaskUseCase;
        public final CheckOffTaskUseCase checkOffTaskUseCase;
        public final RegenerateScheduleUseCase regenerateScheduleUseCase;

        public Bundle(LoadTaskListUseCase loadTaskListUseCase,
                      SaveTaskUseCase saveTaskUseCase,
                      CheckOffTaskUseCase checkOffTaskUseCase,
                      RegenerateScheduleUseCase regenerateScheduleUseCase) {
            this.loadTaskListUseCase = loadTaskListUseCase;
            this.saveTaskUseCase = saveTaskUseCase;
            this.checkOffTaskUseCase = checkOffTaskUseCase;
            this.regenerateScheduleUseCase = regenerateScheduleUseCase;
        }
    }
}
