package com.autosecretary.features.task.application;

import com.autosecretary.features.task.application.listmodel.TaskListItem;
import com.autosecretary.features.task.data.TaskDAO;
import com.autosecretary.features.task.domain.TaskCompletionService;
import com.autosecretary.features.task.domain.TaskLifecycleManager;

import java.util.concurrent.ExecutorService;

/**
 * Orchestrates task completion: reads the task from the database, delegates the
 * two-phase check-off logic to {@link TaskCompletionService}, and writes the
 * results back. The write scope depends on the phase returned.
 */
public class CheckOffTaskUseCase {
    private final TaskDAO taskDao;
    private final TaskCompletionService completionService;
    private final TaskLifecycleManager lifecycleManager;
    private final ExecutorService executor;

    public CheckOffTaskUseCase(TaskDAO taskDao, TaskCompletionService completionService,
                               TaskLifecycleManager lifecycleManager, ExecutorService executor) {
        this.taskDao = taskDao;
        this.completionService = completionService;
        this.lifecycleManager = lifecycleManager;
        this.executor = executor;
    }

    /**
     * Checks off a task slot on a background thread. Reads the full task from the DB,
     * runs the completion logic, persists the changes, and invokes {@code onChanged}
     * so the UI can refresh.
     *
     * @param listItem  the display model identifying the task and slot to check off
     * @param onChanged callback invoked after a successful write, typically triggers a list refresh
     */
    public void execute(TaskListItem listItem, Runnable onChanged) {
        executor.execute(() -> TaskSlotToggleAction.execute(
                taskDao,
                completionService,
                lifecycleManager,
                listItem.taskId,
                listItem.slotId,
                onChanged
        ));
    }
}
