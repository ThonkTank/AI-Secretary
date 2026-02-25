package com.autosecretary.features.task.application;

import com.autosecretary.features.task.data.TaskDAO;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

/**
 * Deletes a task (including dependent task relations/prerequisite links) on a background thread.
 */
public class DeleteTaskUseCase {
    private final TaskDAO taskDao;
    private final ExecutorService workerExecutor;
    private final Executor callbackDispatcher;

    public DeleteTaskUseCase(TaskDAO taskDao,
                             ExecutorService workerExecutor,
                             Executor callbackDispatcher) {
        this.taskDao = taskDao;
        this.workerExecutor = workerExecutor;
        this.callbackDispatcher = callbackDispatcher;
    }

    public void execute(String taskId, Runnable onDeleted) {
        workerExecutor.execute(() -> {
            taskDao.deleteTaskGraph(taskId);
            callbackDispatcher.execute(onDeleted);
        });
    }
}
