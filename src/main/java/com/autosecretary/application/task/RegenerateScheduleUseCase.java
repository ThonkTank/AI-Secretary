package com.autosecretary.application.task;

import com.autosecretary.database.task.TaskDAO;
import com.autosecretary.services.taskPlanning.SlotGenerator;
import com.autosecretary.services.taskPlanning.TaskTreeOperations;
import com.autosecretary.views.taskTab.TaskSeedDataFactory;

import java.util.concurrent.ExecutorService;

public class RegenerateScheduleUseCase {
    private final TaskDAO taskDao;
    private final SlotGenerator generator;
    private final ExecutorService executor;

    public RegenerateScheduleUseCase(TaskDAO taskDao, SlotGenerator generator, ExecutorService executor) {
        this.taskDao = taskDao;
        this.generator = generator;
        this.executor = executor;
    }

    public void execute(Runnable onDone) {
        executor.execute(() -> {
            taskDao.deleteAllCore();
            taskDao.writeList(TaskTreeOperations.flatten(TaskSeedDataFactory.createDefaultTasks()));
            generator.generateSlots();
            onDone.run();
        });
    }
}
