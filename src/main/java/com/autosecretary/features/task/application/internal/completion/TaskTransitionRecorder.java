package com.autosecretary.features.task.application.internal.completion;

import android.util.Log;

import com.autosecretary.features.task.data.TaskDao;
import com.autosecretary.features.task.domain.model.TaskSlot;
import com.autosecretary.features.task.data.TaskTransitionStatDao;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Best-effort recorder for task-to-task transitions used by scheduler learning.
 */
public final class TaskTransitionRecorder {
    private static final String TAG = "TaskTransition";
    private static final int TRANSITION_WEIGHT_STARTED = 1;
    private static final int TRANSITION_WEIGHT_COMPLETED = 2;

    private final TaskDao taskDao;
    private final TaskTransitionStatDao transitionDao;

    public TaskTransitionRecorder(TaskDao taskDao, TaskTransitionStatDao transitionDao) {
        this.taskDao = taskDao;
        this.transitionDao = transitionDao;
    }

    public void recordStarted(TaskSlot slot) {
        record(slot, TRANSITION_WEIGHT_STARTED);
    }

    public void recordCompleted(TaskSlot slot) {
        record(slot, TRANSITION_WEIGHT_COMPLETED);
    }

    private void record(TaskSlot slot, int transitionWeight) {
        try {
            recordInternal(slot, transitionWeight);
        } catch (RuntimeException e) {
            Log.w(TAG, "Transition stat recording failed (non-critical)", e);
        }
    }

    private void recordInternal(TaskSlot slot, int transitionWeight) {
        if (slot == null || slot.taskId == null || slot.day == null) {
            return;
        }

        LocalTime eventTime = slot.realEnd != null ? slot.realEnd
                : slot.realStart != null ? slot.realStart
                : slot.start != null ? slot.start
                : LocalTime.now();

        String previousTaskId = taskDao.readMostRecentTaskBefore(slot.taskId, slot.day, eventTime);
        if (previousTaskId == null || previousTaskId.equals(slot.taskId)) {
            return;
        }

        transitionDao.recordTransition(previousTaskId, slot.taskId, Math.max(1, transitionWeight), LocalDateTime.now());
    }
}
