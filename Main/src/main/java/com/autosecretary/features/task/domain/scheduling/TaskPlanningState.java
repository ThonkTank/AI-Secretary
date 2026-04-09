package com.autosecretary.features.task.domain.scheduling;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Cross-day scheduling state carried between daily scheduling iterations.
 * Tracks which tasks were scheduled on which days, enabling intelligent distribution across the week.
 *
 * <p><strong>Purpose:</strong> When generating a week's schedule, the scheduler processes one day at a time.
 * {@code TaskPlanningState} accumulates information across days so that:
 * <ul>
 *   <li>Repetitions are distributed evenly across the window (not all on day 1)</li>
 *   <li>Recurring tasks are not scheduled on the same day too frequently</li>
 *   <li>Prerequisite ordering and slot placement is aware of earlier days' decisions</li>
 * </ul>
 *
 * <p><strong>Lifecycle:</strong> Create once at the start of multi-day regeneration (e.g., weekly planning).
 * Pass the same instance through each day's scheduling calls. The state is mutated as slots are placed
 * and recorded. Do not reuse a stale instance across regeneration runs.
 *
 * <p><strong>Invariants:</strong>
 * <ul>
 *   <li>{@code scheduledDays} and {@code totalScheduledReps} stay in sync: if a task appears in
 *       {@code scheduledDays}, its total count is also in {@code totalScheduledReps}.</li>
 *   <li>{@code totalScheduledReps} is always {@code >= 0} and {@code >= scheduledDays.size()} for each task.</li>
 * </ul>
 *
 * <p><strong>Thread-safety:</strong> Not thread-safe. Intended for single-threaded use during scheduling.
 *
 * @see TaskSlotGenerator
 */
public final class TaskPlanningState {
    private final Map<String, Set<LocalDate>> scheduledDays = new HashMap<>();
    private final Map<String, Integer> totalScheduledReps = new HashMap<>();

    /**
     * Records that a task was successfully scheduled on a given day.
     *
     * <p>Updates both {@code scheduledDays} and {@code totalScheduledReps} atomically.
     *
     * @param taskId Task UUID to record
     * @param day The day on which the task was placed
     */
    public void recordScheduled(String taskId, LocalDate day) {
        scheduledDays.computeIfAbsent(taskId, k -> new HashSet<>()).add(day);
        totalScheduledReps.merge(taskId, 1, Integer::sum);
    }

    /**
     * Removes a previously recorded scheduled slot. Called by the scheduler when a slot is
     * evicted during competitive displacement, so the evicted task is no longer counted as
     * scheduled on that day.
     *
     * <p>Updates both maps atomically. If no days remain for the task, both entries are cleared.
     * Only decrements the counter if the day was actually found and removed.
     *
     * @param taskId Task UUID to remove
     * @param day The day to remove
     */
    public void removeScheduled(String taskId, LocalDate day) {
        Set<LocalDate> days = scheduledDays.get(taskId);
        if (days != null && days.remove(day)) {
            if (days.isEmpty()) {
                scheduledDays.remove(taskId);
            }
            totalScheduledReps.compute(taskId, (key, value) -> {
                if (value == null || value <= 1) {
                    return null;
                }
                return value - 1;
            });
        }
    }

    /**
     * Returns all days on which the task has been scheduled so far.
     *
     * @param taskId Task UUID to query
     * @return Unmodifiable set of days (empty if never scheduled)
     */
    public Set<LocalDate> getScheduledDays(String taskId) {
        Set<LocalDate> days = scheduledDays.get(taskId);
        return days != null ? Collections.unmodifiableSet(days) : Collections.emptySet();
    }

    /**
     * Returns the total number of times a task has been scheduled across all days.
     *
     * @param taskId Task UUID to query
     * @return Number of scheduled repetitions (0 if never scheduled)
     */
    public int getTotalScheduledReps(String taskId) {
        return totalScheduledReps.getOrDefault(taskId, 0);
    }

    /**
     * Returns the minimum number of days between the given day and any day the task is already scheduled on.
     *
     * <p>Distance is computed using {@code ChronoUnit.DAYS.between()}, which is symmetric and absolute.
     * Used during slot scoring to spread repeated tasks across the window (penalize placing a task
     * on a day very close to a previous scheduling).
     *
     * <p>Example: If task X is scheduled on Monday (day 0) and we're evaluating Wednesday (day 2),
     * the distance is 2 days. If X is scheduled on both Monday and Friday, the distance to Wednesday is 2.
     *
     * @param taskId Task UUID to check
     * @param day Candidate day to evaluate
     * @return Minimum absolute day distance (0 if task already scheduled on that day, {@code Integer.MAX_VALUE} if never scheduled)
     */
    public int minDayDistance(String taskId, LocalDate day) {
        Set<LocalDate> days = scheduledDays.get(taskId);
        if (days == null || days.isEmpty()) return Integer.MAX_VALUE;
        int minDistance = Integer.MAX_VALUE;
        for (LocalDate scheduledDay : days) {
            int distance = (int) Math.abs(ChronoUnit.DAYS.between(scheduledDay, day));
            minDistance = Math.min(minDistance, distance);
        }
        return minDistance;
    }
}
