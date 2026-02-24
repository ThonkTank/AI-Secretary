package com.autosecretary.services;

import com.autosecretary.database.task.Task;
import com.autosecretary.database.task.TaskSlot;

import java.time.Duration;
import java.time.LocalTime;

public class TaskCompletionService {
    private static final long QUICK_TAP_THRESHOLD_SECONDS = 3;
    private static final long STALE_THRESHOLD_SECONDS = 24 * 3600;

    public enum CompletionPhase {
        NONE,
        STARTED,
        COMPLETED
    }

    public CompletionPhase checkOff(Task task, TaskSlot slot, TaskLifecycleManager lifecycleManager) {
        if (slot.completed) return CompletionPhase.NONE;

        if (slot.realStart == null) {
            slot.realStart = LocalTime.now();
            return CompletionPhase.STARTED;
        }

        slot.realEnd = LocalTime.now();
        slot.completed = true;

        long durationSeconds = Duration.between(slot.realStart, slot.realEnd).getSeconds();
        if (durationSeconds < 0) durationSeconds += 24 * 3600;

        boolean isQuickTap = durationSeconds <= QUICK_TAP_THRESHOLD_SECONDS;
        boolean isStale = durationSeconds > STALE_THRESHOLD_SECONDS;
        boolean trackDuration = !isQuickTap && !isStale;

        lifecycleManager.updateStreak(task, slot);
        task.recordCompletion(durationSeconds / 60, trackDuration);

        if (trackDuration && task.core.adaptive) {
            lifecycleManager.adaptPrefSlot(task, slot);
        }
        return CompletionPhase.COMPLETED;
    }
}
