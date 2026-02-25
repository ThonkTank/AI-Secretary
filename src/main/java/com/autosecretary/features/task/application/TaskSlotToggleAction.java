package com.autosecretary.features.task.application;

import com.autosecretary.features.task.data.Task;
import com.autosecretary.features.task.data.TaskDAO;
import com.autosecretary.features.task.data.TaskPrerequisite;
import com.autosecretary.features.task.data.TaskSlot;
import com.autosecretary.features.task.domain.TaskCompletionService;
import com.autosecretary.features.task.domain.TaskCompletionService.CompletionPhase;
import com.autosecretary.features.task.domain.TaskLifecycleManager;

import java.time.LocalDate;

/**
 * Shared operation for toggling a task slot completion state and persisting resulting writes.
 */
public final class TaskSlotToggleAction {
    private TaskSlotToggleAction() {
    }

    public static void execute(TaskDAO taskDao,
                               TaskCompletionService completionService,
                               TaskLifecycleManager lifecycleManager,
                               String taskId,
                               String slotId,
                               Runnable postWriteAction) {
        if (taskId == null || slotId == null) {
            return;
        }

        Task task = taskDao.read(taskId);
        if (task == null) {
            return;
        }

        TaskSlot slot = findSlot(task, slotId);
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
            if (task.core != null && task.core.adaptive) {
                adaptPrerequisiteGaps(taskDao, lifecycleManager, task, slot);
            }
            taskDao.write(task);
        }
        taskDao.writeSlot(slot);

        if (postWriteAction != null) {
            postWriteAction.run();
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

    private static TaskSlot findSlot(Task task, String slotId) {
        if (task.slots == null) {
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
