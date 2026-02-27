package com.autosecretary.features.task.application;

import com.autosecretary.features.task.application.listmodel.TaskListItem;
import com.autosecretary.features.task.data.Task;
import com.autosecretary.features.task.data.TaskDAO;
import com.autosecretary.features.task.data.TaskSlot;
import com.autosecretary.features.task.domain.TaskLifecycleManager;

import java.time.LocalTime;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

/**
 * Increases or decreases progress for a task while keeping progress bounds and
 * completion flags in sync. Routes completions through {@link TaskLifecycleManager}
 * so streaks and history are updated consistently with {@code CheckOffTaskUseCase}.
 *
 * Contract: DAO work runs on {@code executor}; when present, {@code onChanged}
 * runs on {@code callbackDispatcher}.
 */
public class AdjustTaskProgressUseCase {
    private final TaskDAO taskDao;
    private final ExecutorService executor;
    private final Executor callbackDispatcher;
    private final TaskLifecycleManager lifecycleManager;

    public AdjustTaskProgressUseCase(TaskDAO taskDao,
                                     ExecutorService executor,
                                     Executor callbackDispatcher,
                                     TaskLifecycleManager lifecycleManager) {
        this.taskDao = taskDao;
        this.executor = executor;
        this.callbackDispatcher = callbackDispatcher;
        this.lifecycleManager = lifecycleManager;
    }

    public void execute(TaskListItem listItem, boolean increment, Runnable onChanged) {
        int step = Math.max(1, listItem.progressStepDelta);
        int delta = increment ? step : -step;
        executor.execute(() -> {
            if (listItem.taskId == null) return;

            Task task = taskDao.read(listItem.taskId);
            if (task == null || task.core == null || task.core.progress == null || task.core.progress.target <= 0) {
                return;
            }

            int target = task.core.progress.target;
            int current = task.core.progress.current;
            int next = Math.max(0, Math.min(target, current + delta));
            if (next == current) return;

            task.core.progress.current = next;
            boolean completed = next >= target;
            task.core.completed = completed;

            TaskSlot slot = task.findSlot(listItem.slotId);
            if (slot != null) {
                slot.completed = completed;
                if (completed && slot.realEnd == null) {
                    slot.realEnd = LocalTime.now();
                }
                if (completed) {
                    lifecycleManager.updateStreakForCompletion(task, slot);
                    task.recordCompletion(0, false);
                }
            }

            taskDao.write(task);

            if (onChanged != null && callbackDispatcher != null) {
                callbackDispatcher.execute(onChanged);
            }
        });
    }
}
