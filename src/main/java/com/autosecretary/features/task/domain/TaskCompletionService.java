package com.autosecretary.features.task.domain;

import com.autosecretary.features.task.data.Task;
import com.autosecretary.features.task.data.TaskSlot;

import java.time.Duration;
import java.time.LocalTime;

/**
 * Implements a two-phase task completion pattern. The first tap on a task records
 * {@code realStart} and returns {@link CompletionPhase#STARTED}. The second tap
 * records {@code realEnd}, marks the slot completed, and returns
 * {@link CompletionPhase#COMPLETED}. On completion, the elapsed duration is classified
 * as "quick tap" or "stale" to decide whether the duration is meaningful enough to
 * track in history statistics.
 */
public class TaskCompletionService {
    // Completions faster than 3 seconds are "quick taps" — the user just wanted to
    // mark the task done without actually working on it, so duration is not tracked.
    private static final long QUICK_TAP_THRESHOLD_SECONDS = 3;
    // Completions longer than 24 hours mean the user forgot to finish the task in the
    // same session, so the elapsed time is not representative and duration is not tracked.
    private static final long STALE_THRESHOLD_SECONDS = 24 * 3600;

    /**
     * Represents the outcome of a {@link #checkOff} call.
     * <ul>
     *   <li>{@code NONE} — slot was already completed; no-op.</li>
     *   <li>{@code STARTED} — first tap; {@code realStart} recorded.</li>
     *   <li>{@code COMPLETED} — second tap; slot finished, streaks and history updated.</li>
     * </ul>
     */
    public enum CompletionPhase {
        NONE,
        STARTED,
        COMPLETED
    }

    /**
     * Advances a task slot through the two-phase completion flow.
     *
     * @param task             the task owning the slot; mutated on completion (history, streaks)
     * @param slot             the specific time slot being checked off; mutated in both phases
     * @param lifecycleManager handles streak updates and adaptive preferred-time adjustments
     * @return the phase that was executed, so the caller knows what to persist
     */
    public CompletionPhase checkOff(Task task, TaskSlot slot, TaskLifecycleManager lifecycleManager) {
        if (slot.completed) return CompletionPhase.NONE;

        // Phase 1: first tap — record when the user actually started working
        if (slot.realStart == null) {
            slot.realStart = LocalTime.now();
            return CompletionPhase.STARTED;
        }

        // Phase 2: second tap — record end time and finalize
        slot.realEnd = LocalTime.now();
        slot.completed = true;

        long durationSeconds = Duration.between(slot.realStart, slot.realEnd).getSeconds();
        // Handle midnight wraparound (e.g. started 23:50, finished 00:05)
        if (durationSeconds < 0) durationSeconds += 24 * 3600;

        boolean isQuickTap = durationSeconds <= QUICK_TAP_THRESHOLD_SECONDS;
        boolean isStale = durationSeconds > STALE_THRESHOLD_SECONDS;
        // Only track duration in history when the elapsed time is meaningful
        boolean trackDuration = !isQuickTap && !isStale;

        lifecycleManager.updateStreakForCompletion(task, slot);
        int durationMinutes = (int) Math.ceil(durationSeconds / 60.0);
        task.recordCompletion(durationMinutes, trackDuration);
        if (trackDuration) {
            task.core.progress.recordTimingSample(durationMinutes);
            if (task.core.adaptive) {
                lifecycleManager.adaptPrefSlot(task, slot);
            }
        }
        return CompletionPhase.COMPLETED;
    }
}
