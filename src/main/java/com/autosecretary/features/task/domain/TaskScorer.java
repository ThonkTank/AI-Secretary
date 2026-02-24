package com.autosecretary.features.task.domain;

import com.autosecretary.features.task.data.Task;
import com.autosecretary.features.task.data.TaskCore;
import com.autosecretary.features.task.data.TaskPrefSlot;
import com.autosecretary.features.task.data.TaskSlot;
import com.autosecretary.features.task.domain.TaskLifecycleManager;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TaskScorer {

    private static final double DEFAULT_MAX_AGING_MULTIPLIER = 3.0;
    private static final double DEFAULT_PREFERRED_START_DEVIATION_HOURS = 8.0;

    private final TaskLifecycleManager lifecycleManager;
    private final double maxAgingMultiplier;
    private final double preferredStartDeviationHours;
    private final Map<String, ScoringCache> caches = new HashMap<>();

    public TaskScorer(TaskLifecycleManager lifecycleManager) {
        this(lifecycleManager, DEFAULT_MAX_AGING_MULTIPLIER, DEFAULT_PREFERRED_START_DEVIATION_HOURS);
    }

    public TaskScorer(TaskLifecycleManager lifecycleManager, double maxAgingMultiplier, double preferredStartDeviationHours) {
        this.lifecycleManager = lifecycleManager;
        this.maxAgingMultiplier = maxAgingMultiplier;
        this.preferredStartDeviationHours = preferredStartDeviationHours;
    }

    public void reset() {
        caches.clear();
    }

    static class ScoringCache {
        int completions;
        LocalDate lastCompletion;
        int periodCompletions;
        boolean isComplete;
        int scheduledToday;

        int sinceLast;
        double remainingDays;
        double requiredDays;
        double agingForce;
        int maxChildPriority;
        List<TaskPrefSlot> todayPrefSlots;
        int repsPerDay;
        boolean deadlineExpired;
    }

    public void maintenance(Task task) {
        ScoringCache cache = new ScoringCache();
        caches.put(task.core.id, cache);
        LocalDate today = LocalDate.now();
        TaskCore.Repetition rep = task.core.repetition;

        advanceTaskPeriod(task, cache);
        scanSlots(task, cache, today);
        computeCompletionState(task, cache);
        computeDerivedMetrics(task, cache, today);
        computeTodayPrefSlots(task, cache, today.getDayOfWeek());
    }

    /**
     * Advances repetition windows before reading slot-derived counters.
     * <p>
     * Invariant assumptions:
     * <ul>
     *   <li>{@code task} and {@code cache} are non-null.</li>
     *   <li>{@code task.core.repetition} may be null and is handled by {@link TaskLifecycleManager}.</li>
     *   <li>Must be executed before {@link #scanSlots(Task, ScoringCache, LocalDate)} so period boundaries are up-to-date.</li>
     * </ul>
     */
    private void advanceTaskPeriod(Task task, ScoringCache cache) {
        lifecycleManager.advancePeriods(task);
    }

    /**
     * Performs a single pass over all slots and fills raw counters in the cache.
     * <p>
     * Invariant assumptions:
     * <ul>
     *   <li>{@code task}, {@code cache}, and {@code today} are non-null.</li>
     *   <li>{@code task.core.repetition} may be null, have null {@code periodUnit}, or no active period start.</li>
     *   <li>Uses half-open period range {@code [periodStart, periodEnd)} when counting period completions.</li>
     * </ul>
     */
    private void scanSlots(Task task, ScoringCache cache, LocalDate today) {
        cache.completions = 0;
        cache.scheduledToday = 0;
        cache.lastCompletion = task.core.created.minusDays(1);

        TaskCore.Repetition rep = task.core.repetition;
        LocalDate periodStart = (rep != null && rep.periodStart != null) ? rep.periodStart : task.core.created;
        LocalDate periodEnd = (rep != null && rep.periodUnit != null && rep.reps > 0) ? rep.periodEnd() : null;
        cache.periodCompletions = 0;

        for (TaskSlot slot : task.slots) {
            if (slot.completed) {
                cache.completions++;
                if (slot.day.isAfter(cache.lastCompletion)) {
                    cache.lastCompletion = slot.day;
                }
                if (periodEnd != null && !slot.day.isBefore(periodStart) && slot.day.isBefore(periodEnd)) {
                    cache.periodCompletions++;
                }
            }
            if (slot.day.equals(today) && slot.scheduled) {
                cache.scheduledToday++;
            }
        }
    }

    /**
     * Derives completion state from scanned slot counters and repetition settings.
     * <p>
     * Invariant assumptions:
     * <ul>
     *   <li>{@code task} and {@code cache} are non-null.</li>
     *   <li>{@code cache.periodCompletions} and {@code cache.completions} were populated by {@link #scanSlots(Task, ScoringCache, LocalDate)}.</li>
     *   <li>If repetition is disabled ({@code rep == null}, {@code reps <= 0}, or null period unit), completion falls back to any historical completion.</li>
     * </ul>
     */
    private void computeCompletionState(Task task, ScoringCache cache) {
        TaskCore.Repetition rep = task.core.repetition;
        if (rep != null && rep.reps > 0 && rep.periodUnit != null) {
            rep.periodCompletions = cache.periodCompletions;
            cache.isComplete = cache.periodCompletions >= rep.reps;
        } else {
            cache.isComplete = cache.completions > 0;
        }
    }

    /**
     * Computes scoring metrics that depend on current day, deadline, and child priorities.
     * <p>
     * Invariant assumptions:
     * <ul>
     *   <li>{@code task}, {@code cache}, and {@code today} are non-null.</li>
     *   <li>{@code cache.lastCompletion} was initialized during slot scanning.</li>
     *   <li>Deadline is considered expired only when {@code closeOnMiss} is true, deadline exists, and {@code today} is strictly after deadline.</li>
     * </ul>
     */
    private void computeDerivedMetrics(Task task, ScoringCache cache, LocalDate today) {
        cache.sinceLast = (int) ChronoUnit.DAYS.between(cache.lastCompletion, today);
        cache.remainingDays = task.remainingDays();
        cache.requiredDays = task.requiredDays();
        cache.agingForce = Math.min(1 + ((double) cache.sinceLast / 10), maxAgingMultiplier);
        cache.repsPerDay = task.core.repsPerDay();
        cache.deadlineExpired = task.core.closeOnMiss && task.core.deadline != null && today.isAfter(task.core.deadline);

        cache.maxChildPriority = 0;
        for (Task child : task.children) {
            cache.maxChildPriority = Math.max(cache.maxChildPriority, child.core.priority.value);
        }
    }

    /**
     * Filters preferred slots to weekday-compatible entries for today's scoring.
     * <p>
     * Invariant assumptions:
     * <ul>
     *   <li>{@code task}, {@code cache}, and {@code dayOfWeek} are non-null.</li>
     *   <li>{@code TaskPrefSlot.days} may be null and is treated as "not applicable".</li>
     *   <li>Produces a fresh list each run to avoid leaking state across maintenance invocations.</li>
     * </ul>
     */
    private void computeTodayPrefSlots(Task task, ScoringCache cache, DayOfWeek dayOfWeek) {
        cache.todayPrefSlots = new ArrayList<>();
        for (TaskPrefSlot ps : task.prefSlots) {
            if (ps.days != null && ps.days.contains(dayOfWeek)) {
                cache.todayPrefSlots.add(ps);
            }
        }
    }

    public int score(Task task, LocalDateTime start, LocalDateTime end) {
        ScoringCache cache = caches.get(task.core.id);
        if (cache == null) {
            maintenance(task);
            cache = caches.get(task.core.id);
        }

        int availableTime = (int) ChronoUnit.MINUTES.between(start, end);
        if (!isSchedulableNow(task, cache, availableTime)) {
            return 0;
        }

        // prio
        int totalPrio = task.core.priority.value;
        if (cache.maxChildPriority > 0) {
            totalPrio = Math.max(totalPrio, cache.maxChildPriority);
        }

        // fit (todayPrefSlots bereits auf Wochentag gefiltert)
        LocalTime prefStart = null;
        long minDiff = Long.MAX_VALUE;
        for (TaskPrefSlot slot : cache.todayPrefSlots) {
            long slotDiff = Math.abs(Duration.between(start.toLocalTime(), slot.start).toMinutes());
            if (slotDiff < minDiff) {
                minDiff = slotDiff;
                prefStart = slot.start;
            }
        }

        if (prefStart != null) {
            double dif = Duration.between(start.toLocalTime(), prefStart).toMinutes() / 60.0;
            double fit = Math.max(0, 1 - Math.abs(dif / preferredStartDeviationHours));
            totalPrio = (int) (totalPrio * fit);
        }

        // urgency
        double urgency;
        if (cache.remainingDays <= 0) {
            urgency = 100;
        } else if (task.core.deadline != null || (task.core.repetition != null && task.core.repetition.reps > 0)) {
            urgency = 1.0 + cache.requiredDays / cache.remainingDays;
        } else {
            urgency = 1.0;
        }
        totalPrio = (int) (totalPrio * urgency);

        // aging
        totalPrio = (int) (totalPrio * cache.agingForce);

        return totalPrio;
    }

    private boolean isSchedulableNow(Task task, ScoringCache cache, int availableTime) {
        if (cache.isComplete) return false;
        if (cache.scheduledToday >= cache.repsPerDay) return false;
        if (cache.sinceLast < task.core.cooldown) return false;
        if (availableTime < task.core.minDuration) return false;
        if (task.core.progress != null && availableTime < task.core.progress.requiredTimePerRep()) return false;
        return !cache.deadlineExpired;
    }

    public void onSlotAssigned(Task task) {
        ScoringCache cache = caches.get(task.core.id);
        if (cache != null) {
            cache.scheduledToday++;
        }
    }
}
