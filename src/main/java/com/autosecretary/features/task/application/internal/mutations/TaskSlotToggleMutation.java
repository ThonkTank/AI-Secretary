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
 * Implements the two-phase completion pattern: first tap starts the task, second tap finishes it.
 * On completion, updates task history, records task-to-task transitions for scheduler learning,
 * and (for adaptive tasks) updates prerequisite gap timing based on observed completion patterns.
 *
 * Contract: call from a worker thread for DAO reads/writes; when present,
 * callbacks are dispatched through {@code callbackDispatcher}.
 *
 * Completion hook behavior: the {@code completedPhaseHook} consumer is ONLY invoked when:
 * (1) the completion phase is COMPLETED (not STARTED), AND
 * (2) the database transaction succeeds.
 * Callers that pass a hook must expect it to fire conditionally, not unconditionally.
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

    /**
     * Execute the two-phase slot toggle (STARTED → COMPLETED).
     *
     * @param taskId the task UUID
     * @param slotId the slot UUID
     * @param postWriteAction runnable fired after successful writes (always dispatched if writes succeed)
     * @param completedPhaseHook consumer fired only when phase is COMPLETED and writes succeed;
     *        used for side effects like budget booking. May be null if no completion hook is needed.
     */
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
        boolean writeSucceeded;
        if (phase == CompletionPhase.COMPLETED) {
            // Adaptive tasks learn optimal prerequisite gaps from user completion patterns.
            // Reads happen before the transaction to avoid N+1 queries inside the write
            // transaction. Results are applied in-memory to task.prerequisites; taskDao.write(task)
            // below then persists those mutated values atomically.
            if (task.core != null && task.core.adaptive) {
                adaptPrerequisiteGaps(task, slot);
            }
            writeSucceeded = runTransactionOrAbort(database, "Completion write failed", () -> {
                taskDao.write(task);
                recordTransition(slot, TRANSITION_WEIGHT_COMPLETED);
                taskDao.writeSlot(slot);
            });
            if (writeSucceeded && completedPhaseHook != null) {
                completedPhaseHook.accept(task);
            }
        } else {
            writeSucceeded = runTransactionOrAbort(database, "Start write failed", () -> {
                recordTransition(slot, TRANSITION_WEIGHT_STARTED);
                taskDao.writeSlot(slot);
            });
        }
        if (!writeSucceeded) {
            return;
        }

        callbackDispatcher.execute(postWriteAction);
    }

    private static boolean runTransactionOrAbort(RoomDatabase db, String errorMsg, Runnable operation) {
        try {
            db.runInTransaction(operation);
            return true;
        } catch (RuntimeException e) {
            Log.e(TAG, errorMsg, e);
            return false;
        }
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

    /**
     * Record a task-to-task transition for the scheduler's learning algorithm.
     * Transitions represent observed user switching patterns (previous task → current task).
     * These are weighted by completion phase (COMPLETED = 2×, STARTED = 1×) so the scheduler
     * learns which task orders the user actually finishes vs. merely starts.
     *
     * See TaskScorer for how transition statistics influence slot ranking and scheduling
     * preference.
     *
     * @param slot the slot being completed/started
     * @param transitionWeight the relative weight of this transition observation
     */
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

    /**
     * Check whether this slot has the minimum required fields to record a transition.
     * Transitions require both a task ID and a day to look up the previous task.
     *
     * @param slot the slot to check
     * @return true if the slot is complete enough to record a transition
     */
    private static boolean canRecordTransition(TaskSlot slot) {
        return slot.taskId != null && slot.day != null;
    }

    /**
     * Determine the most accurate event time for transition recording.
     * Ordered by preference (actual completion → actual start → scheduled start → current time)
     * to preserve the semantics of when the task-to-task transition actually occurred.
     *
     * @param slot the slot being transitioned
     * @return the best available time point for this slot
     */
    private static LocalTime determineEventTime(TaskSlot slot) {
        if (slot.realEnd != null) return slot.realEnd;
        if (slot.realStart != null) return slot.realStart;
        if (slot.start != null) return slot.start;
        return LocalTime.now();
    }

    /**
     * Check whether this transition is worth recording.
     * A transition is invalid if:
     * - no previous task was found (nothing to transition from)
     * - the previous and current tasks are the same (not a real transition)
     *
     * @param previousTaskId the task that occurred before the current one
     * @param currentTaskId the task being completed/started now
     * @return true if this transition should be skipped
     */
    private static boolean isInvalidTransition(String previousTaskId, String currentTaskId) {
        return previousTaskId == null || previousTaskId.equals(currentTaskId);
    }

}
