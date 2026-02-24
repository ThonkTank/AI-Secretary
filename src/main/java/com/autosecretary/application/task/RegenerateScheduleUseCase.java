package com.autosecretary.application.task;

import com.autosecretary.application.task.port.TaskRepository;
import com.autosecretary.services.taskPlanning.SlotGenerator;
import com.autosecretary.services.taskPlanning.TaskTreeOperations;
import com.autosecretary.views.taskTab.TaskSeedDataFactory;

import java.util.concurrent.ExecutorService;

public class RegenerateScheduleUseCase {
    private final TaskRepository taskRepository;
    private final SlotGenerator generator;
    private final ExecutorService executor;

    public RegenerateScheduleUseCase(TaskRepository taskRepository, SlotGenerator generator, ExecutorService executor) {
        this.taskRepository = taskRepository;
        this.generator = generator;
        this.executor = executor;
    }

    public void execute(Runnable onDone) {
        executor.execute(() -> {
            taskRepository.deleteAllCore();
            taskRepository.writeList(TaskTreeOperations.flatten(TaskSeedDataFactory.createDefaultTasks()));
            generator.generateSlots();
            onDone.run();
        });
    }
}
