package com.autosecretary.features.task.application;

import com.autosecretary.features.task.application.internal.actions.TaskProgressAdjustAction;
import com.autosecretary.features.task.application.listmodel.TaskListItem;
import com.autosecretary.features.task.data.TaskDAO;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

/**
 * Decreases progress for a task while keeping progress bounds and completion flags in sync.
 *
 * Contract: DAO work runs on {@code executor}; when present, {@code onChanged}
 * runs on {@code callbackDispatcher}.
 */
public class DecrementTaskProgressUseCase {
    private final TaskDAO taskDao;
    private final ExecutorService executor;
    private final Executor callbackDispatcher;

    public DecrementTaskProgressUseCase(TaskDAO taskDao,
                                        ExecutorService executor,
                                        Executor callbackDispatcher) {
        this.taskDao = taskDao;
        this.executor = executor;
        this.callbackDispatcher = callbackDispatcher;
    }

    public void execute(TaskListItem listItem, Runnable onChanged) {
        int step = Math.max(1, listItem.progressStepDelta);
        executor.execute(() -> TaskProgressAdjustAction.execute(
                taskDao,
                listItem.taskId,
                listItem.slotId,
                -step,
                callbackDispatcher,
                onChanged
        ));
    }
}
