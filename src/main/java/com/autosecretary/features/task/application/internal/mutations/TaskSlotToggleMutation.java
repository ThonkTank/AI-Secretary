package com.autosecretary.features.task.application.internal.mutations;

import com.autosecretary.features.task.data.Task;
import com.autosecretary.features.task.data.TaskDAO;
import com.autosecretary.features.task.data.TaskPrerequisite;
import com.autosecretary.features.task.data.TaskSlot;
import com.autosecretary.features.task.data.TaskTransitionStatDao;
import com.autosecretary.features.task.domain.TaskCompletionService;
import com.autosecretary.features.task.domain.TaskCompletionService.CompletionPhase;
import com.autosecretary.features.task.domain.TaskLifecycleManager;

import android.util.Log;

import androidx.room.RoomDatabase;

import java.time.LocalDate;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Shared operation for toggling a task slot completion state and persisting resulting writes.
 *
 * Contract: call from a worker thread for DAO reads/writes; when present,
 * callbacks are dispatched through {@code callbackDispatcher}.
 */
public final class TaskSlotToggleMutation {
    // Transition stat weights: completed transitions count double vs. started,
    // so the scheduler gives stronger preference to pairs the user actually finishes.
    private static final int TRANSITION_WEIGHT_STARTED = 1;
    private static final int TRANSITION_WEIGHT_COMPLETED = 2;

    private TaskSlotToggleMutation() {
    }

    public static void execute(TaskDAO taskDao,
                               TaskCompletionService completionService,
                               TaskLifecycleManager lifecycleManager,
                               TaskTransitionStatDao transitionDao,
                               String taskId,
                               String slotId,
                               Executor callbackDispatcher,
                               Runnable postWriteAction,
                               Consumer<Task> completedPhaseHook,
                               RoomDatabase database) {
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

        CompletionPhase phase = completionService.checkOff(task, slot, lifecycleManager);
        if (phase == CompletionPhase.NONE) {
            return;
        }

        // COMPLETED writes the full task because checkOff mutates streak/history fields
        // on the TaskCore. STARTED only touches the slot (set realStart), so writing
        // just the slot avoids an unnecessary full-task upsert.
        if (phase == CompletionPhase.COMPLETED) {
            try {
                database.runInTransaction(() -> {
                    if (task.core != null && task.core.adaptive) {
                        adaptPrerequisiteGaps(taskDao, lifecycleManager, task, slot);
                    }
                    taskDao.write(task);
                    if (completedPhaseHook != null) {
                        completedPhaseHook.accept(task);
                    }
                    TaskTransitionRecorder.record(taskDao, transitionDao, slot, TRANSITION_WEIGHT_COMPLETED);
                    taskDao.writeSlot(slot);
                });
            } catch (RuntimeException e) {
                Log.e("TaskSlotToggle", "Completion write failed", e);
                return;
            }
        } else if (phase == CompletionPhase.STARTED) {
            try {
                database.runInTransaction(() -> {
                    TaskTransitionRecorder.record(taskDao, transitionDao, slot, TRANSITION_WEIGHT_STARTED);
                    taskDao.writeSlot(slot);
                });
            } catch (RuntimeException e) {
                Log.e("TaskSlotToggle", "Start write failed", e);
                return;
            }
        }

        if (postWriteAction != null && callbackDispatcher != null) {
            callbackDispatcher.execute(postWriteAction);
        }
    }

    private static void adaptPrerequisiteGaps(TaskDAO taskDao,
                                              TaskLifecycleManager lifecycleManager,
                                              Task task,
                                              TaskSlot completedSlot) {
        if (task.prerequisites == null || task.prerequisites.isEmpty()) {
            return;
        }

        for (TaskPrerequisite prereq : task.prerequisites) {
            if (prereq.minGapMinutes <= 0) {
                continue;
            }
            Task prereqTask = taskDao.read(prereq.prerequisiteId);
            if (prereqTask == null) {
                continue;
            }
            TaskSlot prereqSlot = findCompletedSlotForDay(prereqTask, completedSlot.day);
            if (prereqSlot == null) {
                continue;
            }
            lifecycleManager.adaptPrerequisiteGap(prereq, prereqSlot, completedSlot);
        }
    }

    private static TaskSlot findCompletedSlotForDay(Task task, LocalDate day) {
        if (task.slots == null) {
            return null;
        }

        for (TaskSlot slot : task.slots) {
            if (slot.day.equals(day) && slot.completed) {
                return slot;
            }
        }
        return null;
    }

}
