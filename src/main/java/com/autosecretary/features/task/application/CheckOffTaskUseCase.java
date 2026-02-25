package com.autosecretary.features.task.application;

import com.autosecretary.features.task.application.listmodel.TaskListItem;
import com.autosecretary.features.task.data.Task;
import com.autosecretary.features.task.data.TaskDAO;
import com.autosecretary.features.task.data.TaskPrerequisite;
import com.autosecretary.features.task.data.TaskSlot;
import com.autosecretary.features.task.domain.TaskCompletionService;
import com.autosecretary.features.task.domain.TaskCompletionService.CompletionPhase;
import com.autosecretary.features.task.domain.TaskLifecycleManager;

import java.time.LocalDate;
import java.util.concurrent.ExecutorService;

/**
 * Orchestrates task completion: reads the task from the database, delegates the
 * two-phase check-off logic to {@link TaskCompletionService}, and writes the
 * results back. The write scope depends on the phase returned.
 */
public class CheckOffTaskUseCase {
    private final TaskDAO taskDao;
    private final TaskCompletionService completionService;
    private final TaskLifecycleManager lifecycleManager;
    private final ExecutorService executor;

    public CheckOffTaskUseCase(TaskDAO taskDao, TaskCompletionService completionService,
                               TaskLifecycleManager lifecycleManager, ExecutorService executor) {
        this.taskDao = taskDao;
        this.completionService = completionService;
        this.lifecycleManager = lifecycleManager;
        this.executor = executor;
    }

    /**
     * Checks off a task slot on a background thread. Reads the full task from the DB,
     * runs the completion logic, persists the changes, and invokes {@code onChanged}
     * so the UI can refresh.
     *
     * @param listItem  the display model identifying the task and slot to check off
     * @param onChanged callback invoked after a successful write, typically triggers a list refresh
     */
    public void execute(TaskListItem listItem, Runnable onChanged) {
        executor.execute(() -> {
            if (listItem.slotId == null) {
                return;
            }

            Task task = taskDao.read(listItem.taskId);
            TaskSlot slot = findSlot(task, listItem.slotId);
            if (slot == null) {
                return;
            }

            CompletionPhase phase = completionService.checkOff(task, slot, lifecycleManager);
            if (phase == CompletionPhase.NONE) {
                return;
            }

            // COMPLETED writes the full task because checkOff mutated streak/history fields
            // on the TaskCore. STARTED only touched the slot (set realStart), so writing
            // just the slot is sufficient and avoids an unnecessary full-task upsert.
            if (phase == CompletionPhase.COMPLETED) {
                if (task.core.adaptive) {
                    adaptPrerequisiteGaps(task, slot);
                }
                taskDao.write(task);
            }
            taskDao.writeSlot(slot);
            onChanged.run();
        });
    }

    private void adaptPrerequisiteGaps(Task task, TaskSlot completedSlot) {
        if (task.prerequisites == null || task.prerequisites.isEmpty()) return;
        for (TaskPrerequisite prereq : task.prerequisites) {
            if (prereq.minGapMinutes <= 0) continue;
            Task prereqTask = taskDao.read(prereq.prerequisiteId);
            if (prereqTask == null) continue;
            TaskSlot prereqSlot = findCompletedSlotForDay(prereqTask, completedSlot.day);
            if (prereqSlot == null) continue;
            lifecycleManager.adaptPrerequisiteGap(prereq, prereqSlot, completedSlot);
        }
    }

    private TaskSlot findCompletedSlotForDay(Task task, LocalDate day) {
        for (TaskSlot slot : task.slots) {
            if (slot.day.equals(day) && slot.completed) {
                return slot;
            }
        }
        return null;
    }

    /** Finds a slot by ID within the task's slot list, or {@code null} if not found. */
    private TaskSlot findSlot(Task task, String slotId) {
        for (TaskSlot slot : task.slots) {
            if (slotId.equals(slot.id)) {
                return slot;
            }
        }
        return null;
    }
}
