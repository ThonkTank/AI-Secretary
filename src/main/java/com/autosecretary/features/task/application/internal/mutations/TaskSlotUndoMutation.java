package com.autosecretary.features.task.application.internal.mutations;

import android.util.Log;

import com.autosecretary.features.task.domain.model.Task;
import com.autosecretary.features.task.domain.model.TaskCore;
import com.autosecretary.features.task.data.TaskDao;
import com.autosecretary.features.task.domain.model.TaskSlot;

import java.time.Duration;
import java.time.LocalDate;
import java.util.concurrent.Executor;

/**
 * Reverts task checkoff by one phase:
 * COMPLETED -> STARTED (keeps realStart) or STARTED -> PENDING.
 */
public final class TaskSlotUndoMutation {
    private static final String TAG = "TaskSlotUndo";
    private static final long SECONDS_PER_DAY = 24 * 3600L;
    private static final long QUICK_TAP_THRESHOLD_SECONDS = 3;
    private static final long STALE_THRESHOLD_SECONDS = SECONDS_PER_DAY;

    private final TaskDao taskDao;
    private final Executor callbackDispatcher;

    public TaskSlotUndoMutation(TaskDao taskDao, Executor callbackDispatcher) {
        this.taskDao = taskDao;
        this.callbackDispatcher = callbackDispatcher;
    }

    public void execute(String taskId, String slotId, Runnable onUndone) {
        if (taskId == null || slotId == null) {
            return;
        }

        Task task = taskDao.read(taskId);
        if (task == null) {
            return;
        }

        TaskSlot slot = task.findSlot(slotId);
        if (slot == null) {
            return;
        }

        if (slot.completed) {
            revertCompletedPhase(task, slot);
            try {
                taskDao.write(task);
            } catch (RuntimeException e) {
                Log.e(TAG, "Undo completion write failed", e);
                return;
            }
            callbackDispatcher.execute(onUndone);
            return;
        }

        if (slot.realStart != null) {
            slot.realStart = null;
            slot.realEnd = null;
            slot.completed = false;
            try {
                taskDao.writeSlot(slot);
            } catch (RuntimeException e) {
                Log.e(TAG, "Undo start write failed", e);
                return;
            }
            callbackDispatcher.execute(onUndone);
        }
    }

    private void revertCompletedPhase(Task task, TaskSlot slot) {
        rollbackTaskHistory(task, slot);
        rollbackRepetitionAndStreak(task, slot);
        slot.completed = false;
        slot.realEnd = null;
    }

    private void rollbackTaskHistory(Task task, TaskSlot slot) {
        if (task.core == null || task.core.history == null) {
            return;
        }
        TaskCore.History history = task.core.history;
        if (history.completions > 0) {
            history.completions--;
        }

        if (!shouldTrackDuration(slot)) {
            return;
        }

        int durationMinutes = completionDurationMinutes(slot);
        if (history.trackedCompletions > 0) {
            history.trackedCompletions--;
        }
        history.totalDuration = Math.max(0, history.totalDuration - durationMinutes);
        if (task.core.progress != null) {
            int progressUnits = task.core.progress.completionProgressUnits();
            task.core.progress.totalTime = Math.max(0, task.core.progress.totalTime - durationMinutes);
            task.core.progress.totalProgress = Math.max(0, task.core.progress.totalProgress - progressUnits);
        }
    }

    private void rollbackRepetitionAndStreak(Task task, TaskSlot slot) {
        if (task.core == null || task.core.history == null) {
            return;
        }
        TaskCore.Repetition repetition = task.core.repetition;
        if (repetition == null || repetition.reps <= 0 || repetition.periodUnit == null) {
            return;
        }

        LocalDate periodStart = repetition.periodStart != null ? repetition.periodStart : task.core.created;
        LocalDate periodEnd = repetition.periodEnd();
        boolean belongsToActivePeriod = periodEnd != null
                && !slot.day.isBefore(periodStart)
                && slot.day.isBefore(periodEnd);

        if (!belongsToActivePeriod || repetition.periodCompletions <= 0) {
            return;
        }

        boolean reachedGoalBeforeUndo = repetition.periodCompletions == repetition.reps;
        // Undo is the inverse of TaskLifecycleManager.updateStreakForCompletion(): it is the
        // only supported path that decrements the persisted period counter.
        repetition.periodCompletions--;
        if (reachedGoalBeforeUndo && task.core.history.currentStreak > 0) {
            task.core.history.currentStreak--;
        }
    }

    private static boolean shouldTrackDuration(TaskSlot slot) {
        long durationSeconds = completionDurationSeconds(slot);
        boolean isQuickTap = durationSeconds <= QUICK_TAP_THRESHOLD_SECONDS;
        boolean isStale = durationSeconds > STALE_THRESHOLD_SECONDS;
        return !isQuickTap && !isStale;
    }

    private static int completionDurationMinutes(TaskSlot slot) {
        return (int) Math.ceil(completionDurationSeconds(slot) / 60.0);
    }

    private static long completionDurationSeconds(TaskSlot slot) {
        if (slot.realStart == null || slot.realEnd == null) {
            return 0;
        }
        long durationSeconds = Duration.between(slot.realStart, slot.realEnd).getSeconds();
        if (durationSeconds < 0) {
            durationSeconds += SECONDS_PER_DAY;
        }
        return durationSeconds;
    }
}
