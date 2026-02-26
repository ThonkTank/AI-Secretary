package com.autosecretary.features.task.application.internal.actions;

import com.autosecretary.features.task.data.Task;
import com.autosecretary.features.task.data.TaskDAO;
import com.autosecretary.features.task.data.TaskSlot;

import java.util.concurrent.Executor;

/**
 * Shared operation for adjusting a task's progress counters and persisting the updates.
 *
 * Contract: call from a worker thread for DAO reads/writes; when present,
 * callbacks are dispatched through {@code callbackDispatcher}.
 */
public final class TaskProgressAdjustAction {
    private TaskProgressAdjustAction() {
    }

    public static void execute(TaskDAO taskDao,
                               String taskId,
                               String slotId,
                               int delta,
                               Executor callbackDispatcher,
                               Runnable postWriteAction) {
        if (taskId == null || delta == 0) {
            return;
        }

        Task task = taskDao.read(taskId);
        if (task == null || task.core == null || task.core.progress == null || task.core.progress.target <= 0) {
            return;
        }

        int target = task.core.progress.target;
        int current = task.core.progress.current;
        int next = Math.max(0, Math.min(target, current + delta));
        if (next == current) {
            return;
        }

        task.core.progress.current = next;
        boolean completed = next >= target;
        task.core.completed = completed;

        TaskSlot slot = findSlot(task, slotId);
        if (slot != null) {
            slot.completed = completed;
        }

        taskDao.write(task);

        if (postWriteAction != null && callbackDispatcher != null) {
            callbackDispatcher.execute(postWriteAction);
        }
    }

    private static TaskSlot findSlot(Task task, String slotId) {
        if (slotId == null || task.slots == null) {
            return null;
        }
        for (TaskSlot slot : task.slots) {
            if (slotId.equals(slot.id)) {
                return slot;
            }
        }
        return null;
    }
}
