package com.autosecretary.features.task.domain;

import com.autosecretary.features.task.data.Task;
import com.autosecretary.features.task.data.TaskCore;
import com.autosecretary.features.task.data.TaskPrefSlot;
import com.autosecretary.features.task.data.TaskSlot;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

/**
 * Stateless domain service managing task period advancement, streak tracking, and
 * adaptive preferred-time adjustment. All methods mutate the passed {@link Task} directly.
 * Used by {@code TaskScorer} ({@link #advancePeriods} during maintenance) and
 * {@code CheckOffTaskUseCase} ({@link #updateStreak} and {@link #adaptPrefSlot} on completion).
 */
public class TaskLifecycleManager {

    // EMA smoothing factor: lower values adapt more slowly, higher values respond faster to recent completions
    private static final double DEFAULT_PREF_SLOT_EMA_ALPHA = 0.2;

    private final double prefSlotEmaAlpha;

    public TaskLifecycleManager() {
        this(DEFAULT_PREF_SLOT_EMA_ALPHA);
    }

    public TaskLifecycleManager(double prefSlotEmaAlpha) {
        this.prefSlotEmaAlpha = prefSlotEmaAlpha;
    }

    /**
     * Advances {@code periodStart} to the current period boundary if the repetition period
     * has expired. Evaluates whether the rep goal was met in the expired period and breaks
     * the streak if not. Also breaks the streak for any skipped empty periods in between.
     */
    public void advancePeriods(Task task) {
        TaskCore.Repetition rep = task.core.repetition;
        if (rep == null || rep.reps <= 0 || rep.periodUnit == null) return;
        if (rep.periodStart == null) rep.periodStart = task.core.created;

        LocalDate now = LocalDate.now();
        int periodDays = rep.periodInDays();
        if (periodDays <= 0) return;
        if (now.isBefore(rep.periodEnd())) return;

        // Evaluate expired period
        boolean goalMet = rep.periodCompletions >= rep.reps;
        if (!goalMet && task.core.history.currentStreak > 0) {
            task.core.history.nrStreaks++;
            task.core.history.currentStreak = 0;
        }

        // Bulk-jump to current period boundary
        long daysSinceStart = ChronoUnit.DAYS.between(rep.periodStart, now);
        long fullPeriods = daysSinceStart / periodDays;
        rep.periodStart = rep.periodStart.plusDays(fullPeriods * periodDays);
        rep.periodCompletions = 0;

        // Skipped empty periods also break the streak
        if (fullPeriods > 1 && task.core.history.currentStreak > 0) {
            task.core.history.nrStreaks++;
            task.core.history.currentStreak = 0;
        }
    }

    /**
     * Increments {@code periodCompletions} by counting completed slots in the current period,
     * then increments {@code currentStreak} only when the period goal is exactly met
     * ({@code periodCompletions == reps}). Streaks are period-based, not consecutive-day-based.
     */
    public void updateStreak(Task task, TaskSlot completedSlot) {
        if (task.core.repetition == null || task.core.repetition.reps <= 0
                || task.core.repetition.periodUnit == null) return;

        advancePeriods(task);

        LocalDate ps = task.core.repetition.periodStart != null ? task.core.repetition.periodStart : task.core.created;
        LocalDate pe = task.core.repetition.periodEnd();
        int count = 0;
        for (TaskSlot s : task.slots) {
            if (s.completed && pe != null && !s.day.isBefore(ps) && s.day.isBefore(pe)) {
                count++;
            }
        }
        task.core.repetition.periodCompletions = count;

        if (count == task.core.repetition.reps) {
            task.core.history.currentStreak++;
        }
    }

    /**
     * Shifts the best-matching {@link TaskPrefSlot#start} toward the actual completion time
     * ({@code slot.realStart}) using an exponential moving average. The smoothing factor
     * {@code alpha} controls adaptation speed (default 0.2). Result is rounded to 5 minutes.
     */
    public void adaptPrefSlot(Task task, TaskSlot slot) {
        if (slot.realStart == null || task.prefSlots == null || task.prefSlots.isEmpty()) return;

        DayOfWeek today = slot.day.getDayOfWeek();
        TaskPrefSlot bestMatch = null;
        long bestDiff = Long.MAX_VALUE;

        for (TaskPrefSlot ps : task.prefSlots) {
            if (ps.days != null && ps.days.contains(today) && ps.start != null) {
                long diff = Math.abs(Duration.between(ps.start, slot.realStart).toMinutes());
                if (diff < bestDiff) {
                    bestDiff = diff;
                    bestMatch = ps;
                }
            }
        }

        if (bestMatch == null) return;

        long prefMinutes = bestMatch.start.toSecondOfDay() / 60;
        long actualMinutes = slot.realStart.toSecondOfDay() / 60;
        long newMinutes = Math.round(prefMinutes * (1 - prefSlotEmaAlpha) + actualMinutes * prefSlotEmaAlpha);
        newMinutes = Math.round(newMinutes / 5.0) * 5; // Round to nearest 5-minute granularity

        bestMatch.start = LocalTime.of((int) (newMinutes / 60), (int) (newMinutes % 60));
    }
}
