package com.autosecretary.application.task;

import com.autosecretary.database.task.Task;
import com.autosecretary.application.task.port.TaskRepository;

import java.util.concurrent.ExecutorService;

public class SaveTaskUseCase {
    private final TaskRepository taskRepository;
    private final ExecutorService executor;

    public SaveTaskUseCase(TaskRepository taskRepository, ExecutorService executor) {
        this.taskRepository = taskRepository;
        this.executor = executor;
    }

    public void execute(Task task, Runnable onSaved) {
        executor.execute(() -> {
            taskRepository.write(task);
            onSaved.run();
        });
    }
}
