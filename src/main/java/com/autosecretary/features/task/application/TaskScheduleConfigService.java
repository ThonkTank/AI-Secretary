package com.autosecretary.features.task.application;

import com.autosecretary.features.task.data.TaskScheduleConfig;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

public class TaskScheduleConfigService {
    private final TaskScheduleConfigRepository repository;
    private final ExecutorService workerExecutor;
    private final Executor callbackDispatcher;

    public TaskScheduleConfigService(TaskScheduleConfigRepository repository,
                                     ExecutorService workerExecutor,
                                     Executor callbackDispatcher) {
        this.repository = repository;
        this.workerExecutor = workerExecutor;
        this.callbackDispatcher = callbackDispatcher;
    }

    public void loadAll(Consumer<List<TaskScheduleConfig>> callback) {
        workerExecutor.execute(() -> {
            List<TaskScheduleConfig> configs = repository.loadAll();
            callbackDispatcher.execute(() -> callback.accept(configs));
        });
    }

    public void saveAll(List<TaskScheduleConfig> configs, Runnable onSaved) {
        workerExecutor.execute(() -> {
            repository.saveAll(configs);
            callbackDispatcher.execute(onSaved);
        });
    }
}
