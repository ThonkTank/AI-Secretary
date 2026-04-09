package com.autosecretary.features.task.domain;

import com.autosecretary.features.task.data.Task;
import com.autosecretary.features.task.data.TaskCore;
import com.autosecretary.features.task.data.TaskPrefSlot;
import com.autosecretary.features.task.data.TaskPrerequisite;
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
 * {@code CheckOffTaskUseCase} ({@link #updateStreakForCompletion} and {@link #adaptPrefSlot} on completion).
 *
 * <p>Edge cases for {@code repetition.completeFirst}:</p>
 * <ul>
 *   <li>Manual backfill: if a user completes an older slot from a previous period, that completion
 *       reduces carryover debt before the current period can accept new reps.</li>
 *   <li>Deadline + completeFirst: an overdue deadline with {@code closeOnMiss=true} still wins in
 *       scoring and can close scheduling even while carryover debt exists.</li>
 * </ul>
 */
public class TaskLifecycleManager {

    // EMA smoothing factor: lower values adapt more slowly, higher values respond faster to recent completions
    private static final double PREF_SLOT_EMA_ALPHA = 0.2;
    private static final int MINUTES_PER_DAY = 24 * 60;

    /**
     * Advances {@code periodStart} to the current period boundary if the repetition period
     * has expired. Evaluates whether the rep goal was met in the expired period and breaks
     * the streak if not. Also breaks the streak for any skipped empty periods in between.
     * Uses {@code LocalDate.now()} as reference — suitable for real-time checkoff flows.
     */
    public void advancePeriods(Task task) {
        advancePeriods(task, LocalDate.now());
    }

    /**
     * Scheduling-day-aware variant of {@link #advancePeriods(Task)}.
     * Uses the given {@code referenceDay} instead of {@code LocalDate.now()} so that
     * multi-day scheduling can correctly advance periods for future days.
     */
    public void advancePeriods(Task task, LocalDate referenceDay) {
        TaskCore.Repetition rep = task.core.repetition;
        if (rep == null || rep.reps <= 0 || rep.periodUnit == null) return;
        if (rep.periodStart == null) rep.periodStart = task.core.created;

        int periodDays = rep.periodInDays();
        if (periodDays <= 0) return;
        if (referenceDay.isBefore(rep.periodEnd())) return;

        // Evaluate expired period
        boolean goalMet = rep.periodCompletions >= rep.reps;
        int missingReps = Math.max(0, rep.reps - rep.periodCompletions);
        if (!goalMet && task.core.history.currentStreak > 0) {
            task.core.history.nrStreaks++;
            task.core.history.currentStreak = 0;
        }

        // Bulk-jump to current period boundary
        long daysSinceStart = ChronoUnit.DAYS.between(rep.periodStart, referenceDay);
        long fullPeriods = daysSinceStart / periodDays;

        if (rep.completeFirst) {
            int carryoverDebt = rep.carryoverDebt + missingReps;
            if (fullPeriods > 1) {
                carryoverDebt += (int) ((fullPeriods - 1) * rep.reps);
            }
            rep.carryoverDebt = Math.max(0, carryoverDebt);
        } else {
            rep.carryoverDebt = 0;
        }

        rep.periodStart = rep.periodStart.plusDays(fullPeriods * periodDays);
        rep.periodCompletions = 0;

        // Skipped empty periods also break the streak
        if (fullPeriods > 1 && task.core.history.currentStreak > 0) {
            task.core.history.nrStreaks++;
            task.core.history.currentStreak = 0;
        }
    }

    /**
     * Applies a single completion event to period counters and streaks.
     *
     * <p>Uses the provided {@code completedSlot} for an incremental update: after advancing
     * periods to "today", {@code periodCompletions} is incremented only when the slot day
     * belongs to the active half-open range {@code [periodStart, periodEnd)}. Then
     * {@code currentStreak} is incremented only when the period goal is exactly met
     * ({@code periodCompletions == reps}). Streaks are period-based, not consecutive-day-based.</p>
     */
    public void updateStreakForCompletion(Task task, TaskSlot completedSlot) {
        if (task.core.repetition == null || task.core.repetition.reps <= 0
                || task.core.repetition.periodUnit == null) return;

        advancePeriods(task, completedSlot.day);

        TaskCore.Repetition rep = task.core.repetition;
        LocalDate periodStart = rep.periodStart != null ? rep.periodStart : task.core.created;
        LocalDate periodEnd = rep.periodEnd();
        boolean belongsToActivePeriod = periodEnd != null
                && !completedSlot.day.isBefore(periodStart)
                && completedSlot.day.isBefore(periodEnd);

        if (rep.completeFirst && rep.carryoverDebt > 0) {
            if (completedSlot.day.isBefore(periodStart)) {
                rep.carryoverDebt = Math.max(0, rep.carryoverDebt - 1);
            }
            return;
        }

        if (belongsToActivePeriod) {
            rep.periodCompletions++;
        }

        if (rep.periodCompletions == rep.reps) {
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
        long minDiff = Long.MAX_VALUE;

        for (TaskPrefSlot ps : task.prefSlots) {
            if (ps.days != null && ps.days.contains(today) && ps.start != null) {
                long diff = Math.abs(Duration.between(ps.start, slot.realStart).toMinutes());
                if (diff < minDiff) {
                    minDiff = diff;
                    bestMatch = ps;
                }
            }
        }

        if (bestMatch == null) return;

        long prefMinutes = bestMatch.start.toSecondOfDay() / 60;
        long actualMinutes = slot.realStart.toSecondOfDay() / 60;
        long newMinutes = ema(prefMinutes, actualMinutes);
        newMinutes = Math.round(newMinutes / 5.0) * 5; // Round to nearest 5-minute granularity

        bestMatch.start = LocalTime.of((int) (newMinutes / 60), (int) (newMinutes % 60));
    }

    /**
     * Adjusts the {@code minGapMinutes} of a prerequisite using an exponential moving
     * average of the actual gap between the prerequisite's completion and the dependent
     * task's start. Rounds to the nearest 5 minutes.
     *
     * @param prereq        the prerequisite relation to adapt
     * @param prereqSlot    the prerequisite task's completed slot for the same day
     * @param dependentSlot the dependent task's completed slot
     */
    public void adaptPrerequisiteGap(TaskPrerequisite prereq, TaskSlot prereqSlot, TaskSlot dependentSlot) {
        if (prereqSlot == null || dependentSlot == null) return;
        if (prereqSlot.realEnd == null || dependentSlot.realStart == null) return;
        if (prereq.minGapMinutes <= 0) return;

        long actualGapMinutes = Duration.between(prereqSlot.realEnd, dependentSlot.realStart).toMinutes();
        if (actualGapMinutes < 0) actualGapMinutes += MINUTES_PER_DAY;

        long newGap = ema(prereq.minGapMinutes, actualGapMinutes);
        newGap = Math.round(newGap / 5.0) * 5;
        newGap = Math.max(0, newGap);

        prereq.minGapMinutes = (int) newGap;
    }

    private static long ema(long current, long sample) {
        return Math.round(current * (1 - PREF_SLOT_EMA_ALPHA) + sample * PREF_SLOT_EMA_ALPHA);
    }
}
