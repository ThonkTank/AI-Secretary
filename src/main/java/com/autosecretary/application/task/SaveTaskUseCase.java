package com.autosecretary.application.task;

import com.autosecretary.database.task.Task;
import com.autosecretary.database.task.TaskDAO;

import java.util.concurrent.ExecutorService;

public class SaveTaskUseCase {
    private final TaskDAO taskDao;
    private final ExecutorService executor;

    public SaveTaskUseCase(TaskDAO taskDao, ExecutorService executor) {
        this.taskDao = taskDao;
        this.executor = executor;
    }

    public void execute(Task task, Runnable onSaved) {
        executor.execute(() -> {
            taskDao.write(task);
            onSaved.run();
        });
    }
}
