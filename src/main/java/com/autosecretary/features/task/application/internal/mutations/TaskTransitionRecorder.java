package com.autosecretary.features.task.application.internal.mutations;

import com.autosecretary.features.task.data.TaskDAO;
import com.autosecretary.features.task.data.TaskSlot;
import com.autosecretary.features.task.data.TaskTransitionStatDao;

import java.time.LocalDateTime;
import java.time.LocalTime;

/** Records observed task execution transitions for scheduling follow-up boosts. */
public final class TaskTransitionRecorder {
    private TaskTransitionRecorder() {
    }

    public static void record(TaskDAO taskDao,
                              TaskTransitionStatDao transitionDao,
                              TaskSlot slot,
                              int delta) {
        if (taskDao == null || transitionDao == null || slot == null || slot.taskId == null || slot.day == null) {
            return;
        }

        LocalTime eventTime = slot.realEnd != null
                ? slot.realEnd
                : (slot.realStart != null ? slot.realStart : (slot.start != null ? slot.start : LocalTime.now()));

        String previousTaskId = taskDao.findMostRecentTaskBefore(slot.taskId, slot.day, eventTime);
        if (previousTaskId == null || previousTaskId.equals(slot.taskId)) {
            return;
        }

        transitionDao.recordTransition(previousTaskId, slot.taskId, Math.max(1, delta), LocalDateTime.now());
    }
}
