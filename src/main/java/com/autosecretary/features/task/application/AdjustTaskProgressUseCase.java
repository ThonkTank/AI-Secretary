package com.autosecretary.features.task.application;

import com.autosecretary.features.task.application.internal.completion.TaskCompletionEffects;
import com.autosecretary.features.task.application.internal.completion.TaskTransitionRecorder;
import com.autosecretary.features.task.application.listmodel.TaskListItem;
import com.autosecretary.features.task.data.Task;
import com.autosecretary.features.task.data.TaskDao;
import com.autosecretary.features.task.data.TaskSlot;
import com.autosecretary.features.task.domain.TaskLifecycleManager;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

/**
 * Increases or decreases progress for a task while keeping progress bounds and
 * completion flags in sync. Routes completions through {@link TaskLifecycleManager}
 * so streaks and history are updated consistently with {@code CheckOffTaskUseCase}.
 *
 * <p><strong>Scope compared to {@code CheckOffTaskUseCase}:</strong> This path still updates
 * streak/history directly on the task, but now also runs the shared post-completion effects
 * (budget booking, meal completion, transition learning) when progress reaches the target.
 *
 * <p>Contract: DAO work runs on {@code workerExecutor}; when present, {@code onChanged}
 * is dispatched via {@code callbackDispatcher}.
 */
public class AdjustTaskProgressUseCase {
    private final TaskDao taskDao;
    private final ExecutorService workerExecutor;
    private final Executor callbackDispatcher;
    private final TaskLifecycleManager lifecycleManager;
    private final TaskCompletionEffects completionEffects;
    private final TaskTransitionRecorder transitionRecorder;

    public AdjustTaskProgressUseCase(TaskDao taskDao,
                                     ExecutorService workerExecutor,
                                     Executor callbackDispatcher,
                                     TaskLifecycleManager lifecycleManager,
                                     TaskCompletionEffects completionEffects,
                                     TaskTransitionRecorder transitionRecorder) {
        this.taskDao = taskDao;
        this.workerExecutor = workerExecutor;
        this.callbackDispatcher = callbackDispatcher;
        this.lifecycleManager = lifecycleManager;
        this.completionEffects = completionEffects;
        this.transitionRecorder = transitionRecorder;
    }

    public void execute(TaskListItem listItem, boolean increment, Runnable onChanged) {
        int step = Math.max(1, listItem.progressStepDelta);
        int delta = increment ? step : -step;
        workerExecutor.execute(() -> {
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
                if (completed) {
                    if (slot.realEnd == null) slot.realEnd = LocalTime.now();
                    lifecycleManager.updateStreakForCompletion(task, slot);
                    // durationMinutes=0: progress-tracked completions don't record session time.
                    // trackDuration=false: skip persisting a duration measurement for this completion.
                    task.recordCompletion(0, false);
                }
            }

            taskDao.write(task);
            if (completed) {
                if (slot != null) {
                    transitionRecorder.recordCompleted(slot);
                }
                completionEffects.apply(task, slot != null ? slot.day : LocalDate.now());
            }

            callbackDispatcher.execute(onChanged);
        });
    }
}
