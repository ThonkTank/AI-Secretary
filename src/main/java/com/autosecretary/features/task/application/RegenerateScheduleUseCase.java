package com.autosecretary.features.task.application;

import com.autosecretary.features.task.data.Task;
import com.autosecretary.features.task.data.TaskDAO;
import com.autosecretary.features.task.domain.SlotGenerator;
import com.autosecretary.features.task.domain.TimeWindow;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

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
            List<Task> scheduledTasks = generator.generateSlots(tasks, windowSupplier.get());
            taskDao.writeList(scheduledTasks);
            onDone.run();
        });
    }
}
