package com.autosecretary.features.task.application.internal.mutations;

import android.util.Log;

import com.autosecretary.features.task.application.internal.completion.TaskTransitionRecorder;
import com.autosecretary.features.task.data.Task;
import com.autosecretary.features.task.data.TaskDao;
import com.autosecretary.features.task.data.TaskPrerequisite;
import com.autosecretary.features.task.data.TaskSlot;
import com.autosecretary.features.task.domain.TaskCompletionService;
import com.autosecretary.features.task.domain.TaskCompletionService.CompletionPhase;
import com.autosecretary.features.task.domain.TaskLifecycleManager;

import java.time.LocalDate;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Shared operation for toggling a task slot completion state and persisting resulting writes.
 * Implements the two-phase completion pattern: first tap starts the task, second tap finishes it.
 * On completion, updates task history, records task-to-task transitions for scheduler learning,
 * and (for adaptive tasks) updates prerequisite gap timing based on observed completion patterns.
 *
 * Contract: call from a worker thread for DAO reads/writes; when present,
 * callbacks are dispatched through {@code callbackDispatcher}.
 *
 * Completion hook behavior: the {@code completedPhaseHook} consumer is ONLY invoked when:
 * (1) the completion phase is COMPLETED (not STARTED), AND
 * (2) the database write succeeds.
 * Callers that pass a hook must expect it to fire conditionally, not unconditionally.
 *
 * Transition stat atomicity: transition stats are analytics data (scheduler learning). They are
 * recorded after the critical write in a separate best-effort operation. If recording fails, core
 * task/slot state is unaffected; the scheduler simply has slightly less learning data.
 */
public final class TaskSlotToggleMutation {
    private static final String TAG = "TaskSlotToggle";

    private final TaskDao taskDao;
    private final TaskCompletionService completionService;
    private final TaskLifecycleManager lifecycleManager;
    private final TaskTransitionRecorder transitionRecorder;
    private final Executor callbackDispatcher;

    public TaskSlotToggleMutation(TaskDao taskDao,
                                  TaskCompletionService completionService,
                                  TaskLifecycleManager lifecycleManager,
                                  TaskTransitionRecorder transitionRecorder,
                                  Executor callbackDispatcher) {
        this.taskDao = taskDao;
        this.completionService = completionService;
        this.lifecycleManager = lifecycleManager;
        this.transitionRecorder = transitionRecorder;
        this.callbackDispatcher = callbackDispatcher;
    }

    /**
     * Execute the two-phase slot toggle (STARTED → COMPLETED).
     *
     * @param taskId the task UUID
     * @param slotId the slot UUID
     * @param onToggled runnable fired after successful writes (always dispatched if writes succeed)
     * @param completedPhaseHook consumer fired only when phase is COMPLETED and writes succeed;
     *        used for side effects like budget booking. May be null if no completion hook is needed.
     */
    public void execute(String taskId,
                        String slotId,
                        Runnable onToggled,
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

        if (phase == CompletionPhase.COMPLETED) {
            // Adaptive tasks learn optimal prerequisite gaps from user completion patterns.
            // Reads happen before the write to avoid N+1 queries inside the write transaction.
            if (task.core != null && task.core.adaptive) {
                adaptPrerequisiteGaps(task, slot);
            }
            // write(task) is @Transaction: atomically writes task core + all slots.
            // This covers both the streak/history update on TaskCore and the slot's
            // realEnd/completed fields in one Room transaction — no RoomDatabase needed.
            try {
                taskDao.write(task);
            } catch (RuntimeException e) {
                Log.e(TAG, "Completion write failed", e);
                return;
            }
            if (completedPhaseHook != null) {
                completedPhaseHook.accept(task);
            }
        } else {
            // STARTED only touches the slot (set realStart), so writing just the slot
            // avoids an unnecessary full-task upsert.
            try {
                taskDao.writeSlot(slot);
            } catch (RuntimeException e) {
                Log.e(TAG, "Start write failed", e);
                return;
            }
        }

        if (phase == CompletionPhase.STARTED) {
            transitionRecorder.recordStarted(slot);
        }

        callbackDispatcher.execute(onToggled);
    }

    /**
     * For adaptive tasks with prerequisites, learn the optimal gap between prerequisite
     * completion and this task's completion based on today's actual completion times.
     * This allows the scheduler to adapt the minimum delay between dependent tasks.
     * Only runs when the task is marked adaptive.
     *
     * @param task the completed adaptive task with prerequisites
     * @param completedSlot the slot that was just completed
     */
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

    /**
     * Find a completed slot for this task on a specific day.
     * Used to locate prerequisites that have already been completed today
     * so their actual completion times can inform prerequisite gap adaptation.
     *
     * @param task the task to search (typically a prerequisite task)
     * @param day the day to search on
     * @return the completed slot on that day, or null if no completion is recorded
     */
    private static TaskSlot findCompletedSlotForDay(Task task, LocalDate day) {
        return task.slots.stream()
                .filter(s -> s.day.equals(day) && s.completed)
                .findFirst()
                .orElse(null);
    }
}
