package com.autosecretary.features.task.application.internal.mutations;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

import android.util.Log;

import androidx.room.RoomDatabase;

import com.autosecretary.features.task.data.Task;
import com.autosecretary.features.task.data.TaskDao;
import com.autosecretary.features.task.data.TaskPrerequisite;
import com.autosecretary.features.task.data.TaskSlot;
import com.autosecretary.features.task.data.TaskTransitionStatDao;
import com.autosecretary.features.task.domain.TaskCompletionService;
import com.autosecretary.features.task.domain.TaskCompletionService.CompletionPhase;
import com.autosecretary.features.task.domain.TaskLifecycleManager;

/**
 * Shared operation for toggling a task slot completion state and persisting resulting writes.
 *
 * Contract: call from a worker thread for DAO reads/writes; when present,
 * callbacks are dispatched through {@code callbackDispatcher}.
 */
public final class TaskSlotToggleMutation {
    private static final String TAG = "TaskSlotToggle";

    // Transition stat weights: completed transitions count double vs. started,
    // so the scheduler gives stronger preference to pairs the user actually finishes.
    private static final int TRANSITION_WEIGHT_STARTED = 1;
    private static final int TRANSITION_WEIGHT_COMPLETED = 2;

    private final TaskDao taskDao;
    private final TaskCompletionService completionService;
    private final TaskLifecycleManager lifecycleManager;
    private final TaskTransitionStatDao transitionDao;
    private final Executor callbackDispatcher;
    private final RoomDatabase database;

    public TaskSlotToggleMutation(TaskDao taskDao,
                                  TaskCompletionService completionService,
                                  TaskLifecycleManager lifecycleManager,
                                  TaskTransitionStatDao transitionDao,
                                  Executor callbackDispatcher,
                                  RoomDatabase database) {
        this.taskDao = taskDao;
        this.completionService = completionService;
        this.lifecycleManager = lifecycleManager;
        this.transitionDao = transitionDao;
        this.callbackDispatcher = callbackDispatcher;
        this.database = database;
    }

    public void execute(String taskId,
                        String slotId,
                        Runnable postWriteAction,
                        Consumer<Task> completedPhaseHook) {
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
        boolean transacted;
        if (phase == CompletionPhase.COMPLETED) {
            transacted = runTransactionOrAbort(database, "Completion write failed", () -> {
                if (task.core != null && task.core.adaptive) {
                    adaptPrerequisiteGaps(task, slot);
                }
                taskDao.write(task);
                recordTransition(slot, TRANSITION_WEIGHT_COMPLETED);
                taskDao.writeSlot(slot);
            });
            if (transacted && completedPhaseHook != null) {
                completedPhaseHook.accept(task);
            }
        } else {
            transacted = runTransactionOrAbort(database, "Start write failed", () -> {
                recordTransition(slot, TRANSITION_WEIGHT_STARTED);
                taskDao.writeSlot(slot);
            });
        }
        if (!transacted) {
            return;
        }

        callbackDispatcher.execute(postWriteAction);
    }

    private static boolean runTransactionOrAbort(RoomDatabase db, String errorMsg, Runnable body) {
        try {
            db.runInTransaction(body);
            return true;
        } catch (RuntimeException e) {
            Log.e(TAG, errorMsg, e);
            return false;
        }
    }

    private void adaptPrerequisiteGaps(Task task, TaskSlot completedSlot) {
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

    private void recordTransition(TaskSlot slot, int transitionWeight) {
        if (!canRecordTransition(slot)) {
            return;
        }

        LocalTime eventTime = determineEventTime(slot);

        String previousTaskId = taskDao.readMostRecentTaskBefore(slot.taskId, slot.day, eventTime);
        if (isInvalidTransition(previousTaskId, slot.taskId)) {
            return;
        }

        transitionDao.recordTransition(previousTaskId, slot.taskId, Math.max(1, transitionWeight), LocalDateTime.now());
    }

    private static boolean canRecordTransition(TaskSlot slot) {
        return slot != null && slot.taskId != null && slot.day != null;
    }

    private static LocalTime determineEventTime(TaskSlot slot) {
        if (slot.realEnd != null) return slot.realEnd;
        if (slot.realStart != null) return slot.realStart;
        if (slot.start != null) return slot.start;
        return LocalTime.now();
    }

    private static boolean isInvalidTransition(String previousTaskId, String currentTaskId) {
        return previousTaskId == null || previousTaskId.equals(currentTaskId);
    }

}
