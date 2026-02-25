package com.autosecretary.features.task.application;

import com.autosecretary.features.task.data.Task;
import com.autosecretary.features.task.data.TaskDAO;
import com.autosecretary.features.task.data.TaskSeedDataFactory;
import com.autosecretary.features.task.domain.SlotGenerator;
import com.autosecretary.features.task.domain.TaskTreeOperations;
import com.autosecretary.features.task.domain.TimeWindow;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

/**
 * Entry point for schedule generation. Reads all tasks from the database, delegates
 * to {@link SlotGenerator} for slot assignment, and writes scheduled results back.
 * Seeds default tasks on first run when the DB is empty.
 */
public class RegenerateScheduleUseCase {
    private final TaskDAO taskDao;
    private final SlotGenerator generator;
    private final ExecutorService executor;
    private final Supplier<TimeWindow> windowSupplier;

    public RegenerateScheduleUseCase(TaskDAO taskDao,
                                     SlotGenerator generator,
                                     Supplier<TimeWindow> windowSupplier,
                                     ExecutorService executor) {
        this.taskDao = taskDao;
        this.generator = generator;
        this.windowSupplier = windowSupplier;
        this.executor = executor;
    }

    public void execute(Runnable onDone) {
        executor.execute(() -> {
            List<Task> tasks = taskDao.readAll();
            // First run: seed default tasks when DB is empty. Flatten tree before
            // writing (Room needs a flat list), then re-read to get proper @Relation assembly.
            if (tasks.isEmpty()) {
                List<Task> seedTasks = TaskSeedDataFactory.createDefaultTasks();
                taskDao.writeList(TaskTreeOperations.flatten(seedTasks));
                tasks = taskDao.readAll();
            }
            List<Task> scheduledTasks = generator.generateSlots(tasks, windowSupplier.get());
            taskDao.writeList(scheduledTasks);
            onDone.run();
        });
    }
}
