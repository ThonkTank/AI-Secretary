package com.autosecretary.application.task;

import com.autosecretary.application.task.port.TaskRepository;
import com.autosecretary.database.task.Task;
import com.autosecretary.services.taskPlanning.SlotGenerator;
import com.autosecretary.services.taskPlanning.TimeWindow;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

public class RegenerateScheduleUseCase {
    private final TaskRepository taskRepository;
    private final SlotGenerator generator;
    private final ExecutorService executor;
    private final Supplier<TimeWindow> windowSupplier;

    public RegenerateScheduleUseCase(TaskRepository taskRepository,
                                     SlotGenerator generator,
                                     Supplier<TimeWindow> windowSupplier,
                                     ExecutorService executor) {
        this.taskRepository = taskRepository;
        this.generator = generator;
        this.windowSupplier = windowSupplier;
        this.executor = executor;
    }

    public void execute(Runnable onDone) {
        executor.execute(() -> {
            List<Task> tasks = taskRepository.readAll();
            List<Task> scheduledTasks = generator.generateSlots(tasks, windowSupplier.get());
            taskRepository.writeList(scheduledTasks);
            onDone.run();
        });
    }
}
