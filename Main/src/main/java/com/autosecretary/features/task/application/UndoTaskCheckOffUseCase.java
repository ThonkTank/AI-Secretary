package com.autosecretary.features.task.application;

import com.autosecretary.features.task.application.internal.mutations.TaskSlotUndoMutation;
import com.autosecretary.features.task.application.listmodel.TaskListItem;

import java.util.concurrent.ExecutorService;

/**
 * Reverts task checkoff by one phase:
 * STARTED -> PENDING or COMPLETED -> STARTED.
 */
public class UndoTaskCheckOffUseCase {
    private final TaskSlotUndoMutation mutation;
    private final ExecutorService workerExecutor;

    public UndoTaskCheckOffUseCase(TaskSlotUndoMutation mutation,
                                   ExecutorService workerExecutor) {
        this.mutation = mutation;
        this.workerExecutor = workerExecutor;
    }

    public void execute(TaskListItem listItem, Runnable onChanged) {
        if (listItem == null) {
            return;
        }
        workerExecutor.execute(() -> mutation.execute(
                listItem.taskId,
                listItem.slotId,
                onChanged
        ));
    }
}
