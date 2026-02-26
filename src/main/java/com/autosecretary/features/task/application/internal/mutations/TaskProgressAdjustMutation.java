package com.autosecretary.features.task.application.internal.mutations;

import com.autosecretary.features.task.data.Task;
import com.autosecretary.features.task.data.TaskDAO;
import com.autosecretary.features.task.data.TaskSlot;

import java.time.LocalTime;
import java.util.concurrent.Executor;

/**
 * Shared operation for adjusting a task's progress counters and persisting the updates.
 *
 * Contract: call from a worker thread for DAO reads/writes; when present,
 * callbacks are dispatched through {@code callbackDispatcher}.
 */
public final class TaskProgressAdjustMutation {
    private TaskProgressAdjustMutation() {
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

        TaskSlot slot = task.findSlot(slotId);
        if (slot != null) {
            slot.completed = completed;
            if (completed && slot.realEnd == null) {
                slot.realEnd = LocalTime.now();
            }
        }

        taskDao.write(task);

        if (postWriteAction != null && callbackDispatcher != null) {
            callbackDispatcher.execute(postWriteAction);
        }
    }

}
