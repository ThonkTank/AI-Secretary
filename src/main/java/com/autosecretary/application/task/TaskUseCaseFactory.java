package com.autosecretary.application.task;

import android.util.Log;

import com.autosecretary.application.task.mapper.TaskListItemMapper;
import com.autosecretary.application.task.port.TaskRepository;
import com.autosecretary.config.Preferences;
import com.autosecretary.services.TaskCompletionService;
import com.autosecretary.services.TaskLifecycleManager;
import com.autosecretary.services.taskPlanning.SlotGenerator;
import com.autosecretary.services.taskPlanning.TaskScorer;
import com.autosecretary.services.taskPlanning.TimeWindow;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class TaskUseCaseFactory {

    private TaskUseCaseFactory() {}

    public static Bundle create(TaskRepository taskRepository, Preferences prefs) {
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
                new LoadTaskListUseCase(taskRepository, mapper, executor),
                new SaveTaskUseCase(taskRepository, executor),
                new CheckOffTaskUseCase(taskRepository, completionService, lifecycleManager, executor),
                new RegenerateScheduleUseCase(taskRepository, generator, () -> {
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
