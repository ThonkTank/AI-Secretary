package com.autosecretary.features.task.application;

import com.autosecretary.features.task.data.TaskDAO;

import java.util.concurrent.ExecutorService;

/**
 * Increases progress for a task while keeping progress bounds and completion flags in sync.
 */
public class IncrementTaskProgressUseCase {
    private final TaskDAO taskDao;
    private final ExecutorService executor;

    public IncrementTaskProgressUseCase(TaskDAO taskDao, ExecutorService executor) {
        this.taskDao = taskDao;
        this.executor = executor;
    }

    public void execute(TaskListItem listItem, Runnable onChanged) {
        int step = Math.max(1, listItem.progressStepDelta);
        executor.execute(() -> TaskProgressAdjustAction.execute(
                taskDao,
                listItem.taskId,
                listItem.slotId,
                step,
                onChanged
        ));
    }
}
